package service

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"golang.org/x/text/encoding/simplifiedchinese"
)

func TestQQProfileClientFetchesGB18030Nickname(t *testing.T) {
	const qqNumber = "123456"
	profiles := map[string][]any{
		qqNumber: {"avatar", -1, 0, 0, 0, 0, "测试昵称", 0},
	}
	encodedJSON, err := json.Marshal(profiles)
	require.NoError(t, err)
	payload, err := simplifiedchinese.GB18030.NewEncoder().String(
		"portraitCallBack(" + string(encodedJSON) + ");",
	)
	require.NoError(t, err)

	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		assert.Equal(t, qqNumber, r.URL.Query().Get("uins"))
		assert.Equal(t, "AndroidResourceDownload-Backend/2.0", r.Header.Get("User-Agent"))
		_, _ = w.Write([]byte(payload))
	}))
	defer server.Close()

	client := NewQQProfileClient()
	client.endpoint = server.URL
	client.httpClient = server.Client()
	nickname, err := client.Nickname(t.Context(), qqNumber)

	require.NoError(t, err)
	assert.Equal(t, "测试昵称", nickname)
}

func TestQQProfileClientRejectsLoginErrorAndWrongIdentity(t *testing.T) {
	tests := map[string]string{
		"login error":    `_Callback({"error":{"type":"need login"}});`,
		"wrong identity": `portraitCallBack({"654321":["avatar",-1,0,0,0,0,"Other",0]});`,
		"numeric name":   `portraitCallBack({"123456":["avatar",-1,0,0,0,0,"123456",0]});`,
	}
	for name, payload := range tests {
		t.Run(name, func(t *testing.T) {
			server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
				_, _ = w.Write([]byte(payload))
			}))
			defer server.Close()

			client := NewQQProfileClient()
			client.endpoint = server.URL
			client.httpClient = server.Client()
			_, err := client.Nickname(t.Context(), "123456")

			require.Error(t, err)
			assert.ErrorIs(t, err, ErrQQProfileUnavailable)
		})
	}
}

func TestQQNumberFromEmailOnlyAcceptsNumericQQAddress(t *testing.T) {
	qqNumber, ok := qqNumberFromEmail(" 123456@QQ.COM ")
	assert.True(t, ok)
	assert.Equal(t, "123456", qqNumber)

	for _, email := range []string{"member@qq.com", "1234@qq.com", "123456@mczihan.link", "123456@qq.com@example.com"} {
		_, ok := qqNumberFromEmail(email)
		assert.False(t, ok, email)
	}
}
