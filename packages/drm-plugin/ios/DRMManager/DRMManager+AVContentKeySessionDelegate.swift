//
//  DRMManager+AVContentKeySessionDelegate.swift
//  ReactNativeVideoDrm
//
//  Created by Krzysztof Moch on 07/08/2025.
//

import AVFoundation
import Foundation
import NitroModules

extension DRMManager: AVContentKeySessionDelegate {
  func contentKeySession(
    _: AVContentKeySession,
    didProvide keyRequest: AVContentKeyRequest
  ) {
    // Check if this should be offline playback
    if let fpsKeyPath = drmParams?.fpsKeyPath,
       FileManager.default.fileExists(atPath: fpsKeyPath) {
      // Request persistable key for offline playback
      do {
        try keyRequest.respondByRequestingPersistableContentKeyRequest()
      } catch {
        handleError(error: error, for: keyRequest)
      }
    } else {
      // Online playback flow (existing)
      handleContentKeyRequest(keyRequest: keyRequest)
    }
  }

  func contentKeySession(
    _: AVContentKeySession,
    didProvideRenewingContentKeyRequest keyRequest: AVContentKeyRequest
  ) {
    handleContentKeyRequest(keyRequest: keyRequest)
  }

  func contentKeySession(
    _: AVContentKeySession,
    shouldRetry _: AVContentKeyRequest,
    reason retryReason: AVContentKeyRequest.RetryReason
  ) -> Bool {
    let retryReasons: [AVContentKeyRequest.RetryReason] = [
      .timedOut,
      .receivedResponseWithExpiredLease,
      .receivedObsoleteContentKey,
    ]
    return retryReasons.contains(retryReason)
  }

  func contentKeySession(
    _: AVContentKeySession,
    didProvide keyRequest: AVPersistableContentKeyRequest
  ) {
    // Load offline key from file
    guard let fpsKeyPath = drmParams?.fpsKeyPath else {
      handleError(
        error: RuntimeError.error(
          withMessage:
            "fpsKeyPath is required for persistable content key requests. Please provide fpsKeyPath in DRM config or use online playback."
        ),
        for: keyRequest
      )
      return
    }

    do {
      // Read offline key data from file
      let keyData = try Data(contentsOf: URL(fileURLWithPath: fpsKeyPath))

      // Create response with the offline key
      let keyResponse = AVContentKeyResponse(
        fairPlayStreamingKeyResponseData: keyData
      )

      // Process the key response
      keyRequest.processContentKeyResponse(keyResponse)

      print("[ReactNativeVideo] DRMManager: Successfully loaded offline FairPlay key from \(fpsKeyPath)")
    } catch {
      handleError(error: error, for: keyRequest)
    }
  }

  func contentKeySession(
    _: AVContentKeySession,
    contentKeyRequest _: AVContentKeyRequest,
    didFailWithError error: Error
  ) {
    // TODO: Handle error appropriately
    print(
      "[ReactNativeVideo] DRMManager: Content key request failed with error: \(error.localizedDescription)"
    )
  }
}
