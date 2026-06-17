# schema.org aligned CMS Admin (Java)

[![Tests](https://github.com/ericbinek/cms-admin-java-ssr/actions/workflows/test.yml/badge.svg)](https://github.com/ericbinek/cms-admin-java-ssr/actions/workflows/test.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
![Version](https://img.shields.io/badge/version-0.2.0-blue.svg)
![Status](https://img.shields.io/badge/status-work_in_progress-orange.svg)
![Build in public](https://img.shields.io/badge/build-in_public-ff69b4.svg)
![PRs welcome](https://img.shields.io/badge/PRs-welcome-brightgreen.svg)
![Java 25](https://img.shields.io/badge/Java-25-orange.svg)

A server rendered admin interface for a schema.org aligned CMS, written in plain Java 25.

It runs on the JDK alone: `com.sun.net.httpserver` serves semantic HTML directly, with no template engine and no runtime dependencies.

It is login protected and offers full create, edit, and delete management for 14 schema.org entity types such as BlogPosting, Person, and Organization. It is a stateless proxy: the browser holds an HttpOnly session cookie, the server translates it into a bearer token for the CMS API, and the API stays the authority for authentication and permissions. State changing forms carry a CSRF synchronizer token.

A conformance test suite defines the markup and behavior.

## Status: work in progress (v0.2.0)

This is an ongoing build-in-public project, shared only for community and communication purposes. Do not deploy it in production. Do not rely on its interfaces or data format remaining stable.

## No framework

There is no Spring, no Jakarta, and no Maven plugins doing work behind your back. The HTTP layer is `com.sun.net.httpserver`, JSON is handled by a small hand-written parser, and the build is a plain `javac`. If you know the JDK, you already know how this runs.

## Requirements

- JDK 25 or newer

## Building

```sh
find src -name "*.java" > sources.txt && javac -d out @sources.txt && rm sources.txt
```

## Running

```sh
java -cp out cms.Server
```

The server listens on `PORT` (default 5006).

## Usage

Open http://localhost:5006/ in a browser and sign in. Accounts live in the CMS API; there is no self-registration.
Each entity has a list view at `/<plural>`, a detail view at `/<plural>/:id`, and create/edit/delete flows.

Configure the upstream API via the `API_BASE_URL` environment variable. Set `COOKIE_SECURE=true` when serving over HTTPS.

## Entities

- `BlogPosting`
- `Person`
- `Organization`
- `WebPage`
- `ImageObject`
- `VideoObject`
- `AudioObject`
- `CategoryCode`
- `CategoryCodeSet`
- `DefinedTerm`
- `DefinedTermSet`
- `Comment`
- `WebSite`
- `SiteNavigationElement`

## Testing

```sh
java -cp out cms.test.TestRunner
```

## Contributing

Contributions are welcome. This is a build-in-public project, so issues, questions, and ideas count as much as pull requests. If you send code, keep it on the JDK standard library with no new dependencies, and keep the conformance suite green, since the tests are the contract. Run them with `java -cp out cms.test.TestRunner`.

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full guidelines.

## License

MIT. See [LICENSE](LICENSE).
