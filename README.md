# KMP-ShareKit

[![License: MIT](https://img.shields.io/badge/License-Apache%202.0-brightgreen.svg?style=flat-square)](LICENSE)

### Overview

Kotlin multiplatform library that implements content sharing for Android & iOS platforms. Share text, images, and video seamlessly.

| Content type | Status |
|----------|--------|
| text/plain | ✅ |
| text/html  | 👷 |
| image/* | 👷 |
| video/* | 👷 |

### Getting Started

#### Android

You need to implement the 'activityProvider' to provide the required Activity.

```kotlin
// MainActivity.kt

ShareKit.setActivityProvider { return@setActivityProvider this }
```

### Usage

```kotlin
ShareKit.shareText(text)
```
