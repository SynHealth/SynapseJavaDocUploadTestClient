# Changelog

All notable changes to the Document Uploader Test Client will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## [1.1.0](https://github.com/SynHealth/SynapseJavaDocUploadTestClient/releases/tag/v1.1.0) - 2026-08-14

### Changed
- Upgraded to Spring Boot 4.1.0 and Bouncy Castle 1.85
- Authentication now uses Microsoft Entra ID. The scope must be given in
  `.default` form, for example `api://fulfill-api-phi/.default`
- Document upload endpoints moved to `integrations-api-phi.synapsehealth.dev`
- Minimum runtime is now Java 21

### Removed
- Unused dependencies: the embedded servlet container, Spring Security OAuth2,
  Spring Cloud Consul config, and a redundant second Jackson stack

### Fixed
- The client secret is no longer written to logs when `--verbose` is used

## 1.0.0 - 2025-06-26

### Added
- Initial release with core functionality
- OAuth2 client credentials authentication
- Document signing with RSA private keys (SHA256withRSA)
- Support for both FHIR Binary and REST API endpoints
- Detailed error reporting and validation
- Configuration via YAML or properties files
- Comprehensive documentation for customers

