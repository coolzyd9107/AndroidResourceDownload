package service

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"strings"
	"time"
	"unicode"
	"unicode/utf8"

	"golang.org/x/text/encoding/simplifiedchinese"
)

// QQProfileLookup resolves a public QQ nickname from a numeric QQ account.
type QQProfileLookup interface {
	Nickname(ctx context.Context, qqNumber string) (string, error)
}

// QQProfileClient queries Tencent's QQ Zone portrait endpoint.
type QQProfileClient struct {
	httpClient *http.Client
	endpoint   string
}

// NewQQProfileClient creates a bounded client that never follows redirects.
func NewQQProfileClient() *QQProfileClient {
	return &QQProfileClient{
		httpClient: &http.Client{
			Timeout: 4 * time.Second,
			CheckRedirect: func(_ *http.Request, _ []*http.Request) error {
				return http.ErrUseLastResponse
			},
		},
		endpoint: "https://users.qzone.qq.com/fcg-bin/cgi_get_portrait.fcg",
	}
}

// ErrQQProfileUnavailable means Tencent did not return a usable public profile.
var ErrQQProfileUnavailable = errors.New("qq profile unavailable")

// Nickname returns the public nickname for qqNumber.
func (c *QQProfileClient) Nickname(ctx context.Context, qqNumber string) (string, error) {
	if !validQQNumber(qqNumber) {
		return "", fmt.Errorf("%w: invalid QQ number", ErrQQProfileUnavailable)
	}
	endpoint, err := url.Parse(c.endpoint)
	if err != nil {
		return "", fmt.Errorf("%w: invalid endpoint", ErrQQProfileUnavailable)
	}
	query := endpoint.Query()
	query.Set("uins", qqNumber)
	endpoint.RawQuery = query.Encode()

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, endpoint.String(), nil)
	if err != nil {
		return "", fmt.Errorf("%w: create request", ErrQQProfileUnavailable)
	}
	req.Header.Set("Accept", "*/*")
	req.Header.Set("User-Agent", "AndroidResourceDownload-Backend/2.0")

	resp, err := c.httpClient.Do(req)
	if err != nil {
		return "", fmt.Errorf("%w: request failed", ErrQQProfileUnavailable)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("%w: status %d", ErrQQProfileUnavailable, resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, maxQQProfileResponseBytes+1))
	if err != nil || len(body) > maxQQProfileResponseBytes {
		return "", fmt.Errorf("%w: invalid response body", ErrQQProfileUnavailable)
	}
	payload, err := decodeQQProfileResponse(body)
	if err != nil {
		return "", err
	}
	return parseQQPortraitNickname(payload, qqNumber)
}

func decodeQQProfileResponse(body []byte) (string, error) {
	if utf8.Valid(body) {
		return string(body), nil
	}
	decoded, err := simplifiedchinese.GB18030.NewDecoder().Bytes(body)
	if err != nil || !utf8.Valid(decoded) {
		return "", fmt.Errorf("%w: invalid encoding", ErrQQProfileUnavailable)
	}
	return string(decoded), nil
}

func parseQQPortraitNickname(payload, qqNumber string) (string, error) {
	trimmed := strings.TrimSuffix(strings.TrimSpace(payload), ";")
	const prefix = "portraitCallBack("
	if !strings.HasPrefix(trimmed, prefix) || !strings.HasSuffix(trimmed, ")") {
		return "", fmt.Errorf("%w: invalid callback", ErrQQProfileUnavailable)
	}
	var profiles map[string][]json.RawMessage
	if err := json.Unmarshal([]byte(trimmed[len(prefix):len(trimmed)-1]), &profiles); err != nil {
		return "", fmt.Errorf("%w: invalid JSON", ErrQQProfileUnavailable)
	}
	values := profiles[qqNumber]
	if len(values) <= 6 {
		return "", fmt.Errorf("%w: profile missing", ErrQQProfileUnavailable)
	}
	var nickname string
	if err := json.Unmarshal(values[6], &nickname); err != nil {
		return "", fmt.Errorf("%w: nickname missing", ErrQQProfileUnavailable)
	}
	nickname = strings.TrimSpace(nickname)
	if !validQQNickname(nickname, qqNumber) {
		return "", fmt.Errorf("%w: invalid nickname", ErrQQProfileUnavailable)
	}
	return nickname, nil
}

func qqNumberFromEmail(email string) (string, bool) {
	local, domain, ok := strings.Cut(strings.TrimSpace(email), "@")
	if !ok || strings.Contains(domain, "@") || !strings.EqualFold(domain, "qq.com") || !validQQNumber(local) {
		return "", false
	}
	return local, true
}

func validQQNumber(value string) bool {
	if len(value) < 5 || len(value) > 12 {
		return false
	}
	for _, ch := range value {
		if ch < '0' || ch > '9' {
			return false
		}
	}
	return true
}

func validQQNickname(value, qqNumber string) bool {
	if value == "" || value == qqNumber || utf8.RuneCountInString(value) > 100 || strings.ContainsRune(value, '\uFFFD') {
		return false
	}
	for _, ch := range value {
		if unicode.IsControl(ch) {
			return false
		}
	}
	return true
}

const maxQQProfileResponseBytes = 16 << 10
