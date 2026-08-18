package dto

// UpdateInfoResponse is the payload returned by GET /api/v1/update/info.
type UpdateInfoResponse struct {
	VersionCode  int64  `json:"versionCode"`
	VersionName  string `json:"versionName"`
	ForceUpdate  bool   `json:"forceUpdate"`
	Changelog    string `json:"changelog"`
	EncryptedURL string `json:"encryptedUrl"`
	ExpiresAt    int64  `json:"expiresAt"`
	Signature    string `json:"signature"`
}

// UpdateResolveRequest is the body for POST /api/v1/update/resolve.
type UpdateResolveRequest struct {
	EncryptedURL string `json:"encryptedUrl" binding:"required"`
}

// UpdateResolveResponse is the payload returned by POST /api/v1/update/resolve.
type UpdateResolveResponse struct {
	URL       string `json:"url"`
	ExpiresIn int    `json:"expiresIn"`
}

// UpdateURLPayload is the plaintext sealed inside encryptedUrl.
type UpdateURLPayload struct {
	VersionCode int64  `json:"versionCode"`
	TargetURL   string `json:"targetUrl"`
	ExpiresAt   int64  `json:"exp"`
	JTI         string `json:"jti"`
}
