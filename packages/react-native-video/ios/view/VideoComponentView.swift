//
//  VideoComponent.swift
//  ReactNativeVideo
//
//  Created by Krzysztof Moch on 30/09/2024.
//

import Foundation
import UIKit
import AVFoundation
import AVKit

@objc public class VideoComponentView: UIView {
  public weak var player: HybridVideoPlayerSpec? = nil {
    didSet {
      guard let player = player as? HybridVideoPlayer else { return }
      configureAVPlayerViewController(with: player.player)
    }
  }
  
  var delegate: VideoViewDelegate?
  private var playerView: UIView? = nil
  
  private var observer: VideoComponentViewObserver? {
    didSet {
      playerViewController?.delegate = observer
      observer?.updatePlayerViewControllerObservers()
    }
  }
  
  private var _keepScreenAwake: Bool = false
  var keepScreenAwake: Bool {
    get {
      guard let player = player as? HybridVideoPlayer else { return false }
      return player.player.preventsDisplaySleepDuringVideoPlayback
    }
    set {
      guard let player = player as? HybridVideoPlayer else { return }
      player.player.preventsDisplaySleepDuringVideoPlayback = newValue
      _keepScreenAwake = newValue
    }
  }
  
  var playerViewController: AVPlayerViewController? {
    didSet {
      guard let observer, let playerViewController else { return }
      playerViewController.delegate = observer
      observer.updatePlayerViewControllerObservers()
    }
  }
  
  public var controls: Bool = false {
    didSet {
      DispatchQueue.main.async { [weak self] in
        guard let self = self, let playerViewController = self.playerViewController else { return }
        playerViewController.showsPlaybackControls = self.controls
      }
    }
  }
  
  public var allowsPictureInPicturePlayback: Bool = false {
    didSet {
      DispatchQueue.main.async { [weak self] in
        guard let self = self, let playerViewController = self.playerViewController else { return }
        
        VideoManager.shared.requestAudioSessionUpdate()
        playerViewController.allowsPictureInPicturePlayback = self.allowsPictureInPicturePlayback
      }
    }
  }
  
  public var autoEnterPictureInPicture: Bool = false {
    didSet {
      DispatchQueue.main.async { [weak self] in
        guard let self = self, let playerViewController = self.playerViewController else { return }
        
        VideoManager.shared.requestAudioSessionUpdate()
        playerViewController.canStartPictureInPictureAutomaticallyFromInline = self.allowsPictureInPicturePlayback
      }
    }
  }
  
  public var resizeMode: ResizeMode = .none {
    didSet {
      DispatchQueue.main.async { [weak self] in
        guard let self = self, let playerViewController = self.playerViewController else { return }
        playerViewController.videoGravity = resizeMode.toVideoGravity()
      }
    }
  }
  
  @objc public var nitroId: NSNumber = -1 {
    didSet {
      VideoComponentView.globalViewsMap.setObject(self, forKey: nitroId)
    }
  }
  
  @objc public static var globalViewsMap: NSMapTable<NSNumber, VideoComponentView> = .strongToWeakObjects()
  
  @objc public override init(frame: CGRect) {
    super.init(frame: frame)
    VideoManager.shared.register(view: self)
    setupPlayerView()
    observer = VideoComponentViewObserver(view: self)
  }
  
  deinit {
    VideoManager.shared.unregister(view: self)
  }
  
  @objc public required init?(coder: NSCoder) {
    super.init(coder: coder)
    setupPlayerView()
  }
  
  func setNitroId(nitroId: NSNumber) -> Void {
    self.nitroId = nitroId
  }
  
  private func setupPlayerView() {
    // Create a UIView to hold the video player layer
    playerView = UIView(frame: self.bounds)
    playerView?.translatesAutoresizingMaskIntoConstraints = false
    if let playerView = playerView {
      addSubview(playerView)
      NSLayoutConstraint.activate([
        playerView.leadingAnchor.constraint(equalTo: self.leadingAnchor),
        playerView.trailingAnchor.constraint(equalTo: self.trailingAnchor),
        playerView.topAnchor.constraint(equalTo: self.topAnchor),
        playerView.bottomAnchor.constraint(equalTo: self.bottomAnchor)
      ])
    }
  }
  
  public func configureAVPlayerViewController(with player: AVPlayer, attempt: Int = 0) {
    DispatchQueue.main.async { [weak self] in
      guard let self = self, let playerView = self.playerView else { return }

      let controller = AVPlayerViewController()
      // `view` is `UIView!`; AVKit has returned nil here in production, which trapped on the
      // implicit unwrap. Read it as an optional and retry on a later main-queue pass.
      guard let controllerView = controller.view as UIView? else {
        if attempt < 3 {
          self.configureAVPlayerViewController(with: player, attempt: attempt + 1)
        }
        return
      }

      // Remove previous controller if any. Detach its player first so AVKit tears down its
      // AVPlayer KVO before the controller leaves the hierarchy.
      self.playerViewController?.player = nil
      self.playerViewController?.willMove(toParent: nil)
      self.playerViewController?.viewIfLoaded?.removeFromSuperview()
      self.playerViewController?.removeFromParent()

      controller.player = player
      controller.showsPlaybackControls = controls
      controller.videoGravity = self.resizeMode.toVideoGravity()
      controllerView.frame = playerView.bounds
      controllerView.autoresizingMask = [.flexibleWidth, .flexibleHeight]
      controllerView.backgroundColor = .clear
      
      // We manage this manually in NowPlayingInfoCenterManager
      controller.updatesNowPlayingInfoCenter = false
      
      if #available(iOS 16.0, *) {
        if let initialSpeed = controller.speeds.first(where: { $0.rate == player.rate }) {
          controller.selectSpeed(initialSpeed)
        }
      }
      
      // Find nearest UIViewController
      if let parentVC = self.findViewController() {
        parentVC.addChild(controller)
        playerView.addSubview(controllerView)
        controller.didMove(toParent: parentVC)
        self.playerViewController = controller
      }
    }
  }
  
  // Helper to find nearest UIViewController
  private func findViewController() -> UIViewController? {
    var responder: UIResponder? = self
    while let r = responder {
      if let vc = r as? UIViewController {
        return vc
      }
      responder = r.next
    }
    return nil
  }
  
  public override func willMove(toSuperview newSuperview: UIView?) {
    super.willMove(toSuperview: newSuperview)
    
    if newSuperview == nil {
      PluginsRegistry.shared.notifyVideoViewDestroyed(view: self)

      // Detach the AVPlayer from the controller on main before the view is torn down, so
      // AVKit's KVO on it is removed before the player's item is replaced or released.
      // UIKit calls this on main; runs inline. This intentionally ends any active PiP
      // session for this view (the app does not use PiP today).
      playerViewController?.player = nil
      
      // We want to disable this when view is about to unmount
      if keepScreenAwake {
        keepScreenAwake = false
      }
    } else {
      PluginsRegistry.shared.notifyVideoViewCreated(view: self)

      // Re-attach the player detached on a previous removal (remount).
      if let playerViewController, playerViewController.player == nil,
        let hybridPlayer = player as? HybridVideoPlayer
      {
        playerViewController.player = hybridPlayer.player
      }
      
      // We want to restore keepScreenAwake after component remount
      if _keepScreenAwake {
        keepScreenAwake = true
      }
    }
  }


  public override func layoutSubviews() {
    super.layoutSubviews()

    // Update the frame of the playerViewController's view when the view's layout changes
    playerViewController?.viewIfLoaded?.frame = playerView?.bounds ?? .zero
    playerViewController?.contentOverlayView?.frame = playerView?.bounds ?? .zero
    for subview in playerViewController?.contentOverlayView?.subviews ?? [] {
      subview.frame = playerView?.bounds ?? .zero
    }
  }
  
  public func enterFullscreen() throws {
    guard let playerViewController else {
      throw VideoViewError.viewIsDeallocated.error()
    }
    
    DispatchQueue.main.async {
      playerViewController.enterFullscreen(animated: true)
    }
  }
  
  public func exitFullscreen() throws {
    guard let playerViewController else {
      throw VideoViewError.viewIsDeallocated.error()
    }
    
    DispatchQueue.main.async {
      playerViewController.exitFullscreen(animated: true)
    }
  }
  
  public func startPictureInPicture() throws {
    guard let playerViewController else {
      throw VideoViewError.viewIsDeallocated.error()
    }
    
    guard AVPictureInPictureController.isPictureInPictureSupported() else {
      throw VideoViewError.pictureInPictureNotSupported.error()
    }
    
    DispatchQueue.main.async {
      // Here we skip error handling for simplicity
      // We do check for PiP support earlier in the code
      try? playerViewController.startPictureInPicture()
    }
  }
  
  public func stopPictureInPicture() throws {
    guard let playerViewController else {
      throw VideoViewError.viewIsDeallocated.error()
    }
    
    DispatchQueue.main.async {
      // Here we skip error handling for simplicity
      // We do check for PiP support earlier in the code
      playerViewController.stopPictureInPicture()
    }
  }
}
