FROM golang:1.23-alpine AS builder

WORKDIR /src
RUN apk add --no-cache git ca-certificates

COPY go.mod go.sum ./
RUN go mod download

COPY . .
RUN CGO_ENABLED=0 GOOS=linux go build -ldflags="-s -w" -o /out/server ./cmd/server

FROM alpine:3.20

RUN apk add --no-cache ca-certificates tzdata && \
    addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=builder /out/server /app/server

ENV APP_ENV=prod \
    SERVER_PORT=8080 \
    DATABASE_DRIVER=postgres

USER app
EXPOSE 8080
ENTRYPOINT ["/app/server"]
