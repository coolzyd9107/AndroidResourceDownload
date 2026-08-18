package dto

// CredentialRequest is the body for POST /api/v1/webdav/credential.
// MVP supports empty body; reserved fields are kept for the future ECDH flow.
type CredentialRequest struct {
	ClientPublicKey string `json:"clientPublicKey,omitempty"`
	KeyType         string `json:"keyType,omitempty"`
}

// CredentialPayload is the plaintext sealed inside encryptedCredential.
type CredentialPayload struct {
	BaseURL    string `json:"baseUrl"`
	Username   string `json:"username"`
	Password   string `json:"password"`
	RootPath   string `json:"rootPath"`
	Permission string `json:"permission"`
	ExpiresAt  int64  `json:"expiresAt"`
	JTI        string `json:"jti"`
}

// CredentialResponse is the MVP /webdav/credential response (HTTPS, JSON).
type CredentialResponse struct {
	BaseURL    string `json:"baseUrl"`
	Username   string `json:"username"`
	Password   string `json:"password"`
	RootPath   string `json:"rootPath"`
	Permission string `json:"permission"`
	ExpiresAt  int64  `json:"expiresAt"`
}
