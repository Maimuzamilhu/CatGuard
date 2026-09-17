# Security Policy

## Core Security Principle: 100% Offline & Zero Telemetry

CatGuard is designed from the ground up as a private, standalone security appliance:
- **No Internet Access**: The `android.permission.INTERNET` permission is deliberately excluded from `AndroidManifest.xml`.
- **No Analytics / Telemetry**: No analytics libraries, crash-reporting SDKs, or cloud services are present in the codebase.
- **On-Device Processing**: All image analysis and inference with TensorFlow Lite are executed strictly on the local device's CPU/GPU. No camera frames or detection metadata ever leave the physical hardware.
- **Minimal Permissions**: Legacy permissions like `READ_PHONE_STATE`, `READ_EXTERNAL_STORAGE`, and `WRITE_EXTERNAL_STORAGE` are explicitly removed via manifest merger rules.

## Reporting a Security Concern

If you believe you have discovered a vulnerability, security flaw, or permission regression in CatGuard, please report it responsibly.

### How to Report
- Open a GitHub Security Advisory (if enabled on the repository) or email the maintainer directly.
- Include a description of the issue, steps to reproduce, and the device/Android OS version tested.

### What We Promise
- We will acknowledge receipt of your report within 48 hours.
- We will provide an assessment and timeline for a patch.
- We will credit you in the release notes (if desired) once resolved.
