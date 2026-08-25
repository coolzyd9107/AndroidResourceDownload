package integration

import (
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// qqGraphStub is a local stand-in for graph.qq.com used by QQ login tests.
type qqGraphStub struct {
	server   *httptest.Server
	openID   string
	clientID string
	nickname string
	meBody   string
}

func newQqGraphStub(t *testing.T, openID, clientID, nickname string) *qqGraphStub {
	t.Helper()
	stub := &qqGraphStub{openID: openID, clientID: clientID, nickname: nickname}
	mux := http.NewServeMux()
	mux.HandleFunc("/oauth2.0/me", func(w http.ResponseWriter, r *http.Request) {
		if stub.meBody != "" {
			_, _ = w.Write([]byte(stub.meBody))
			return
		}
		w.Header().Set("Content-Type", "application/javascript")
		_, _ = w.Write([]byte(`callback( {"client_id":"` + stub.clientID + `","openid":"` + stub.openID + `"} );`))
	})
	mux.HandleFunc("/user/get_user_info", func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte(`{"ret":0,"msg":"","nickname":"` + stub.nickname + `","figureurl_qq_2":"https://thirdqq.qlogo.cn/qq_app/100"}`))
	})
	stub.server = httptest.NewServer(mux)
	t.Cleanup(stub.server.Close)
	return stub
}

func configureQqForTest(t *testing.T, baseURL string) {
	t.Helper()
	t.Setenv("QQ_APP_ID", "100123456")
	t.Setenv("QQ_ME_URL", baseURL+"/oauth2.0/me")
	t.Setenv("QQ_USER_INFO_URL", baseURL+"/user/get_user_info")
}

func TestQqLoginHappyPath(t *testing.T) {
	const openID = "0123456789ABCDEF0123456789ABCDEF"
	stub := newQqGraphStub(t, openID, "100123456", "小明")
	configureQqForTest(t, stub.server.URL)
	env := newTestEnv(t)

	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/qq/login",
		map[string]string{"accessToken": "provider-token", "openId": openID, "deviceId": "device-1"}, "")
	require.Equal(t, http.StatusOK, code)
	require.EqualValues(t, 0, body["code"])

	data := body["data"].(map[string]any)
	user := data["user"].(map[string]any)
	assert.Equal(t, "QQ", user["loginType"])
	assert.Equal(t, "USER", user["role"])
	assert.Equal(t, "小明", user["name"])
	assert.Equal(t, "https://thirdqq.qlogo.cn/qq_app/100", user["avatarUrl"])
	assert.NotEmpty(t, data["accessToken"])
	assert.NotEmpty(t, data["refreshToken"])

	token, _ := data["accessToken"].(string)
	meCode, meBody := env.doJSON(t, http.MethodGet, "/api/v1/auth/me", nil, token)
	assert.Equal(t, http.StatusOK, meCode)
	meData := meBody["data"].(map[string]any)
	assert.Equal(t, "QQ", meData["loginType"])

	stub.nickname = "新名字"
	replayCode, replayBody := env.doJSON(t, http.MethodPost, "/api/v1/auth/qq/login",
		map[string]string{"accessToken": "provider-token-2", "openId": openID}, "")
	require.Equal(t, http.StatusOK, replayCode)
	replayUser := replayBody["data"].(map[string]any)["user"].(map[string]any)
	assert.Equal(t, user["id"], replayUser["id"])
	assert.Equal(t, "新名字", replayUser["name"])
}

func TestQqLoginRejectsOpenIDMismatch(t *testing.T) {
	stub := newQqGraphStub(t, "FEDCBA9876543210FEDCBA9876543210", "100123456", "小明")
	configureQqForTest(t, stub.server.URL)
	env := newTestEnv(t)

	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/qq/login",
		map[string]string{"accessToken": "provider-token", "openId": "0123456789ABCDEF0123456789ABCDEF"}, "")
	assert.Equal(t, http.StatusUnauthorized, code)
	assert.EqualValues(t, 10008, body["code"])
}

func TestQqLoginRejectsInvalidRequestBody(t *testing.T) {
	stub := newQqGraphStub(t, "0123456789ABCDEF0123456789ABCDEF", "100123456", "小明")
	configureQqForTest(t, stub.server.URL)
	env := newTestEnv(t)

	code, body := env.doJSON(t, http.MethodPost, "/api/v1/auth/qq/login",
		map[string]string{"accessToken": ""}, "")
	assert.Equal(t, http.StatusBadRequest, code)
	assert.Equal(t, "invalid_request", body["message"])
}
