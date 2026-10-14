import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useOutletContext, useNavigate } from 'react-router-dom';
import { Html5Qrcode } from 'html5-qrcode';
import { assetService } from '../services/assetService';
import { trackingService } from '../services/trackingService';
import { transferService } from '../services/transferService';
import { useAuth } from '../context/AuthContext';
import { getGpsAccuracyCategory } from '../utils/gpsTelemetry';

const SAMPLE_QRS = [
  { code: 'ECG-001', name: 'Portable ECG Machine', qr: 'MEDTRACK:ASSET:ECG-001', file: '/qr-samples/ecg-001.png', dept: 'Cardiology' },
  { code: 'PUMP-001', name: 'Infusion Pump', qr: 'MEDTRACK:ASSET:PUMP-001', file: '/qr-samples/pump-001.png', dept: 'ICU' },
  { code: 'MON-001', name: 'Patient Monitor', qr: 'MEDTRACK:ASSET:MON-001', file: '/qr-samples/mon-001.png', dept: 'Emergency' },
  { code: 'INVALID-999', name: 'Unregistered Demo Code', qr: 'MEDTRACK:ASSET:INVALID-999', file: '/qr-samples/invalid-999.png', dept: 'None' },
];

// Telemetry network throttle interval (5 seconds between POST submissions)
const TELEMETRY_THROTTLE_MS = 5000;

export default function QrScannerPage() {
  const navigate = useNavigate();
  const { onSelectAsset } = useOutletContext() || {};
  const { user, roles } = useAuth();

  const isAdminOrSupervisor =
    roles?.includes('ADMIN') ||
    roles?.includes('SUPERVISOR') ||
    roles?.includes('ROLE_ADMIN') ||
    roles?.includes('ROLE_SUPERVISOR') ||
    user?.role === 'ADMIN' ||
    user?.role === 'SUPERVISOR';

  // Scanner status: 'INITIALIZING' | 'SCANNING' | 'DETECTED' | 'VERIFYING' | 'SUCCESS' | 'NOT_FOUND' | 'ERROR'
  const [scannerState, setScannerState] = useState('INITIALIZING');
  const [statusMessage, setStatusMessage] = useState('Starting camera...');
  const [errorMessage, setErrorMessage] = useState(null);

  // Decoded & Verified data
  const [decodedValue, setDecodedValue] = useState('');
  const [verifiedAsset, setVerifiedAsset] = useState(null);
  const [activeTransfer, setActiveTransfer] = useState(null);

  // Real Mobile GPS Tracking status: 'READY' | 'ACQUIRING' | 'ACTIVE' | 'ERROR' | 'STOPPED'
  const [trackingState, setTrackingState] = useState('READY');
  const [gpsCoords, setGpsCoords] = useState({
    latitude: null,
    longitude: null,
    accuracy: null,
    speed: null,
    heading: null,
    timestamp: null,
  });
  const [gpsErrorMessage, setGpsErrorMessage] = useState(null);
  const [activeSession, setActiveSession] = useState(null);
  const [telemetryCount, setTelemetryCount] = useState(0);
  const [lastUpdateAgo, setLastUpdateAgo] = useState('Just now');

  // Camera management
  const [cameras, setCameras] = useState([]);
  const [selectedCameraId, setSelectedCameraId] = useState(null);
  const [torchAvailable, setTorchAvailable] = useState(false);
  const [isTorchOn, setIsTorchOn] = useState(false);

  // Camera Diagnostic state (Direct getUserMedia Probe)
  const [showDiagnosticModal, setShowDiagnosticModal] = useState(false);
  const [diagnosticLoading, setDiagnosticLoading] = useState(false);
  const [diagnosticResult, setDiagnosticResult] = useState(null);

  // Manual input & Samples modal
  const [manualCode, setManualCode] = useState('');
  const [showSamplesModal, setShowSamplesModal] = useState(false);

  // Refs for lifecycle & throttling
  const html5QrCodeRef = useRef(null);
  const isScanningRef = useRef(false);
  const isMountedRef = useRef(true);
  const fileInputRef = useRef(null);
  const mutationObserverRef = useRef(null);
  const directStreamRef = useRef(null);

  // Geolocation & session refs
  const watchIdRef = useRef(null);
  const lastSentTimeRef = useRef(0);
  const lastFixTimeRef = useRef(null);
  const verifiedAssetRef = useRef(null);
  const activeSessionRef = useRef(null);
  const relativeTimerRef = useRef(null);
  const trackingStateRef = useRef(trackingState);
  const sessionStartAttemptRef = useRef(0);

  // Keep refs in sync with state
  useEffect(() => {
    trackingStateRef.current = trackingState;
  }, [trackingState]);

  useEffect(() => {
    verifiedAssetRef.current = verifiedAsset;
  }, [verifiedAsset]);

  useEffect(() => {
    activeSessionRef.current = activeSession;
  }, [activeSession]);

  // Relative timer updating "X seconds ago"
  useEffect(() => {
    if (trackingState === 'ACTIVE' && lastFixTimeRef.current) {
      relativeTimerRef.current = setInterval(() => {
        if (!lastFixTimeRef.current) return;
        const diffSeconds = Math.max(0, Math.floor((Date.now() - lastFixTimeRef.current) / 1000));
        if (diffSeconds < 2) {
          setLastUpdateAgo('Just now');
        } else {
          setLastUpdateAgo(`${diffSeconds} seconds ago`);
        }
      }, 1000);
    } else {
      if (relativeTimerRef.current) {
        clearInterval(relativeTimerRef.current);
        relativeTimerRef.current = null;
      }
    }
    return () => {
      if (relativeTimerRef.current) {
        clearInterval(relativeTimerRef.current);
      }
    };
  }, [trackingState]);

  // Warn user if navigating away or closing window while GPS tracking is active
  useEffect(() => {
    if (trackingState !== 'ACTIVE') return;

    const handleBeforeUnload = (e) => {
      e.preventDefault();
      e.returnValue = 'Real-time GPS tracking is actively running. Navigating away will stop the tracking session.';
      return e.returnValue;
    };

    window.addEventListener('beforeunload', handleBeforeUnload);
    return () => {
      window.removeEventListener('beforeunload', handleBeforeUnload);
    };
  }, [trackingState]);

  // Stop camera helper that completely terminates video tracks and wipes injected DOM elements
  const stopCameraStream = useCallback(async () => {
    if (mutationObserverRef.current) {
      mutationObserverRef.current.disconnect();
      mutationObserverRef.current = null;
    }

    const container = document.getElementById('qr-reader-viewport');
    if (container) {
      const videos = container.querySelectorAll('video');
      videos.forEach((v) => {
        if (v.srcObject && typeof v.srcObject.getTracks === 'function') {
          v.srcObject.getTracks().forEach((track) => {
            try {
              track.stop();
            } catch {}
          });
          v.srcObject = null;
        }
      });
    }

    if (directStreamRef.current) {
      try {
        directStreamRef.current.getTracks().forEach((t) => t.stop());
      } catch {}
      directStreamRef.current = null;
    }

    if (html5QrCodeRef.current && isScanningRef.current) {
      try {
        await html5QrCodeRef.current.stop();
      } catch (err) {
        console.warn('Camera stop caught:', err);
      } finally {
        isScanningRef.current = false;
      }
    }

    if (container) {
      container.innerHTML = '';
    }
  }, []);

  // Stop GPS watch and active session
  const stopGpsTracking = useCallback(async () => {
    sessionStartAttemptRef.current += 1;

    if (watchIdRef.current !== null && 'geolocation' in navigator) {
      try {
        navigator.geolocation.clearWatch(watchIdRef.current);
      } catch (err) {
        console.warn('clearWatch error:', err);
      }
      watchIdRef.current = null;
    }

    if (relativeTimerRef.current) {
      clearInterval(relativeTimerRef.current);
      relativeTimerRef.current = null;
    }

    const currentSession = activeSessionRef.current;
    activeSessionRef.current = null;
    setActiveSession(null);

    if (currentSession?.id) {
      try {
        await trackingService.stopSession(currentSession.id);
      } catch (err) {
        if (err?.response?.status !== 404) {
          console.warn('Stop session API error:', err);
        }
      }
    }
  }, []);

  // Backend verification of scanned or entered QR string
  const verifyQrCode = useCallback(async (qrText) => {
    if (!qrText || !qrText.trim()) return;
    const cleanQr = qrText.trim();
    setDecodedValue(cleanQr);
    setScannerState('VERIFYING');
    setStatusMessage('Verifying asset in MedTrack database...');
    setTrackingState('READY');
    setGpsErrorMessage(null);

    try {
      const asset = await assetService.getAssetByQr(cleanQr);
      if (!isMountedRef.current) return;
      setVerifiedAsset(asset);
      try {
        const activeTx = await transferService.getActiveTransferForAsset(asset.id);
        if (isMountedRef.current) setActiveTransfer(activeTx);
      } catch {
        if (isMountedRef.current) setActiveTransfer(null);
      }
      setScannerState('SUCCESS');
      setStatusMessage('Asset verified successfully');
    } catch (err) {
      if (!isMountedRef.current) return;
      console.warn('QR verification error:', err);
      setVerifiedAsset(null);
      setActiveTransfer(null);
      setScannerState('NOT_FOUND');
      setStatusMessage('This QR code is not registered in MedTrack.');
    }
  }, []);

  // Callback when QR is decoded from video feed
  const onScanSuccess = useCallback(async (decodedText) => {
    if (!isMountedRef.current) return;
    setScannerState('DETECTED');
    setStatusMessage('QR detected');

    await stopCameraStream();
    await verifyQrCode(decodedText);
  }, [stopCameraStream, verifyQrCode]);

  // Friendly mapping for Android & iOS camera errors (no stack traces displayed to user)
  const mapCameraError = (err) => {
    const name = err?.name || '';
    const msg = (err?.message || String(err)).toLowerCase();

    if (name === 'NotAllowedError' || msg.includes('permission') || msg.includes('notallowed')) {
      return 'Camera access was denied. Allow camera access in Settings and try again.';
    }
    if (name === 'NotFoundError' || msg.includes('devicesnotfound') || msg.includes('notfound') || msg.includes('no camera')) {
      return 'Camera unavailable. No camera was detected on this device. You can verify assets using manual code entry.';
    }
    if (name === 'NotReadableError' || msg.includes('notreadable') || msg.includes('trackstart') || msg.includes('camera already in use')) {
      return 'Camera is already in use by another application or tab. Please close other camera apps and retry.';
    }
    if (name === 'OverconstrainedError' || msg.includes('overconstrained') || msg.includes('unsupported constraint')) {
      return 'Camera constraint not supported by your lens. Retrying with default camera configuration...';
    }
    if (name === 'AbortError' || msg.includes('aborterror')) {
      return 'Camera hardware stream was interrupted. Tap "Tap to Start Camera" to try again.';
    }
    if (name === 'SecurityError' || msg.includes('securityerror')) {
      return 'Camera access is restricted by security policy. Please ensure HTTPS is used.';
    }
    return 'Camera access was denied or unavailable. Allow camera access in Settings and try again.';
  };

  // Enumerate cameras without disrupting active stream
  const enumerateCameras = useCallback(async () => {
    if (!navigator?.mediaDevices?.enumerateDevices) return [];
    try {
      const allDevices = await navigator.mediaDevices.enumerateDevices();
      const videoDevices = allDevices.filter((d) => d.kind === 'videoinput');
      const formatted = videoDevices.map((d, index) => {
        const label = d.label || `Camera ${index + 1}`;
        const isRear = /back|rear|environment|wide|main|0,/i.test(label);
        return {
          id: d.deviceId,
          label,
          isRear,
        };
      });
      // Preferred order:
      // 1. rear/environment camera
      // 2. valid selected camera deviceId
      // 3. available camera
      formatted.sort((a, b) => (b.isRear ? 1 : 0) - (a.isRear ? 1 : 0));
      if (isMountedRef.current && formatted.length > 0) {
        setCameras(formatted);
      }
      return formatted;
    } catch (e) {
      console.warn('enumerateDevices error:', e);
      return [];
    }
  }, []);

  // Enforce iOS WebKit & Android video attributes, dimensions check, and clean lifecycle
  const applyVideoAttributes = useCallback((video) => {
    if (!video) return;
    video.setAttribute('autoplay', 'true');
    video.setAttribute('muted', 'true');
    video.setAttribute('playsinline', 'true');
    video.setAttribute('webkit-playsinline', 'true');
    video.autoplay = true;
    video.muted = true;
    video.playsInline = true;
    video.style.width = '100%';
    video.style.height = '100%';
    video.style.objectFit = 'cover';
    video.style.display = 'block';

    const handleLoadedMetadata = () => {
      if (video.videoWidth > 0 && video.videoHeight > 0) {
        console.log(`Video metadata ready: ${video.videoWidth}x${video.videoHeight}, readyState=${video.readyState}`);
      }
      if (video.paused) {
        video.play().catch(() => {});
      }
    };

    const handleCanPlay = () => {
      if (video.paused) {
        video.play().catch(() => {});
      }
    };

    const handlePause = () => {
      // Recover only if genuinely paused while scanner is active, avoid permanent polling
      if (isScanningRef.current && video.srcObject && video.paused) {
        video.play().catch(() => {});
      }
    };

    video.removeEventListener('loadedmetadata', handleLoadedMetadata);
    video.removeEventListener('canplay', handleCanPlay);
    video.removeEventListener('pause', handlePause);

    video.addEventListener('loadedmetadata', handleLoadedMetadata, { once: true });
    video.addEventListener('canplay', handleCanPlay, { once: true });
    video.addEventListener('pause', handlePause);

    if (video.paused) {
      video.play().catch(() => {});
    }
  }, []);

  // Start / restart scanner with facingMode: { ideal: "environment" } or specific camera ID
  const startScanner = useCallback(async (cameraIdToUse = null) => {
    setErrorMessage(null);
    setScannerState('INITIALIZING');
    setStatusMessage('Accessing device camera...');

    try {
      await stopCameraStream();
      if (html5QrCodeRef.current) {
        try {
          await html5QrCodeRef.current.clear();
        } catch {}
      }

      const viewportElem = document.getElementById('qr-reader-viewport');
      if (!viewportElem) return;
      viewportElem.innerHTML = '';

      // Observe DOM mutations to immediately apply required video attributes
      if (mutationObserverRef.current) {
        mutationObserverRef.current.disconnect();
      }
      const observer = new MutationObserver((mutations) => {
        for (const mutation of mutations) {
          for (const node of mutation.addedNodes) {
            if (node.tagName === 'VIDEO') {
              applyVideoAttributes(node);
            } else if (node.querySelectorAll) {
              node.querySelectorAll('video').forEach(applyVideoAttributes);
            }
          }
        }
      });
      observer.observe(viewportElem, { childList: true, subtree: true });
      mutationObserverRef.current = observer;

      const scanner = new Html5Qrcode('qr-reader-viewport', { verbose: false });
      html5QrCodeRef.current = scanner;

      // Android + iOS requirements:
      // Use facingMode: { ideal: "environment" }
      // Do NOT initially force { exact: "environment" }
      const videoConstraints = cameraIdToUse
        ? { deviceId: { ideal: cameraIdToUse } }
        : { facingMode: { ideal: 'environment' } };

      const config = {
        fps: 15,
        videoConstraints,
      };

      const cameraParam = cameraIdToUse || { facingMode: 'environment' };

      await scanner.start(
        cameraParam,
        config,
        onScanSuccess,
        () => {} // scan error frame
      );

      const videoElem = viewportElem.querySelector('video');
      if (videoElem) {
        applyVideoAttributes(videoElem);
      }

      if (!isMountedRef.current) {
        await scanner.stop();
        return;
      }

      isScanningRef.current = true;
      setScannerState('SCANNING');
      setStatusMessage('Scanning...');

      // Enumerate cameras AFTER permission granted without creating a new stream
      const availableCameras = await enumerateCameras();
      if (availableCameras.length > 0 && !cameraIdToUse) {
        setSelectedCameraId(availableCameras[0].id);
      } else if (cameraIdToUse) {
        setSelectedCameraId(cameraIdToUse);
      }

      try {
        const capabilities = scanner.getRunningTrackCapabilities();
        if (capabilities && capabilities.torch) {
          setTorchAvailable(true);
        }
      } catch {
        setTorchAvailable(false);
      }
    } catch (err) {
      if (!isMountedRef.current) return;
      console.warn('Scanner start error:', err);
      isScanningRef.current = false;
      setScannerState('ERROR');
      setErrorMessage(mapCameraError(err));
    }
  }, [stopCameraStream, onScanSuccess, enumerateCameras, applyVideoAttributes]);

  // Direct getUserMedia Diagnostic probe
  const runDirectGetUserMediaDiagnostic = async () => {
    setShowDiagnosticModal(true);
    setDiagnosticLoading(true);
    setDiagnosticResult(null);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: { ideal: 'environment' },
        },
        audio: false,
      });

      const videoTrack = stream.getVideoTracks()[0];
      const settings = videoTrack.getSettings ? videoTrack.getSettings() : {};

      const diagData = {
        success: true,
        trackLabel: videoTrack.label || 'Default Camera',
        facingMode: settings.facingMode || 'environment',
        settingsWidth: settings.width || 0,
        settingsHeight: settings.height || 0,
        readyState: videoTrack.readyState,
        streamActive: stream.active,
        timestamp: new Date().toLocaleTimeString(),
      };

      setDiagnosticResult(diagData);

      // Attach directly to diagnostic video element
      setTimeout(() => {
        const diagVideo = document.getElementById('diagnostic-preview-video');
        if (diagVideo) {
          diagVideo.setAttribute('autoplay', 'true');
          diagVideo.setAttribute('muted', 'true');
          diagVideo.setAttribute('playsinline', 'true');
          diagVideo.setAttribute('webkit-playsinline', 'true');
          diagVideo.autoplay = true;
          diagVideo.muted = true;
          diagVideo.playsInline = true;
          diagVideo.srcObject = stream;
          diagVideo.onloadedmetadata = () => {
            diagVideo.play().catch(() => {});
            setDiagnosticResult((prev) => ({
              ...prev,
              actualVideoWidth: diagVideo.videoWidth,
              actualVideoHeight: diagVideo.videoHeight,
              videoReadyState: diagVideo.readyState,
            }));
          };
        }
      }, 100);

      directStreamRef.current = stream;
    } catch (err) {
      console.error('Direct getUserMedia failed:', err);
      setDiagnosticResult({
        success: false,
        errorName: err.name,
        errorMessage: mapCameraError(err),
        timestamp: new Date().toLocaleTimeString(),
      });
    } finally {
      setDiagnosticLoading(false);
    }
  };

  const closeDiagnosticModal = () => {
    if (directStreamRef.current) {
      try {
        directStreamRef.current.getTracks().forEach((t) => t.stop());
      } catch {}
      directStreamRef.current = null;
    }
    setShowDiagnosticModal(false);
    setDiagnosticResult(null);
  };

  // Lifecycle: mount once on initial render, stop both camera & gps on unmount
  useEffect(() => {
    isMountedRef.current = true;

    const timer = setTimeout(() => {
      startScanner();
    }, 200);

    return () => {
      isMountedRef.current = false;
      clearTimeout(timer);
      stopCameraStream();
      stopGpsTracking();
    };
  }, []); // Run only on initial mount

  // Torch toggle handler
  const handleToggleTorch = async () => {
    if (!html5QrCodeRef.current || !torchAvailable) return;
    try {
      const nextState = !isTorchOn;
      await html5QrCodeRef.current.applyVideoConstraints({
        advanced: [{ torch: nextState }],
      });
      setIsTorchOn(nextState);
    } catch (err) {
      console.warn('Torch toggle error:', err);
    }
  };

  // Switch camera handler
  const handleSwitchCamera = async (e) => {
    const newCameraId = e.target.value;
    setSelectedCameraId(newCameraId);
    await startScanner(newCameraId);
  };

  // Scan from uploaded or sample image file
  const handleFileUpload = async (e) => {
    const file = e.target.files?.[0];
    if (!file) return;

    setScannerState('VERIFYING');
    setStatusMessage('Decoding QR image...');

    try {
      await stopCameraStream();
      const scanner = html5QrCodeRef.current || new Html5Qrcode('qr-reader-viewport', { verbose: false });
      const decodedResult = await scanner.scanFile(file, true);
      await verifyQrCode(decodedResult);
    } catch (err) {
      console.warn('File decode error:', err);
      setScannerState('NOT_FOUND');
      setDecodedValue(file.name);
      setStatusMessage('Unable to read a valid QR code from the uploaded image.');
    } finally {
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // Manual code entry submission
  const handleManualSubmit = async (e) => {
    e.preventDefault();
    if (!manualCode.trim()) return;
    await stopCameraStream();
    await verifyQrCode(manualCode.trim());
    setManualCode('');
  };

  // Reset and restart scanner
  const handleRestartScanner = async () => {
    await stopGpsTracking();
    setTrackingState('READY');
    setGpsCoords({ latitude: null, longitude: null, accuracy: null, speed: null, heading: null, timestamp: null });
    setGpsErrorMessage(null);
    setActiveSession(null);
    setTelemetryCount(0);
    setVerifiedAsset(null);
    setActiveTransfer(null);
    setDecodedValue('');
    setScannerState('INITIALIZING');
    setStatusMessage('Accessing device camera...');
    setTimeout(() => {
      startScanner(selectedCameraId);
    }, 50);
  };

  // ==========================================
  // REAL MOBILE GPS TRACKING HANDLERS
  // ==========================================

  // START TRACKING: Confirms/starts server session FIRST, then activates real browser GPS watchPosition
  const handleStartTracking = useCallback(async () => {
    if (typeof window !== 'undefined' && window.isSecureContext === false) {
      setGpsErrorMessage('Device Geolocation requires a secure HTTPS connection. Please ensure MedTrack is accessed over HTTPS.');
      setTrackingState('ERROR');
      return;
    }

    if (!('geolocation' in navigator)) {
      setGpsErrorMessage('Geolocation is not supported by this browser.');
      setTrackingState('ERROR');
      return;
    }

    const currentAsset = verifiedAssetRef.current;
    if (!currentAsset) {
      setGpsErrorMessage('No verified asset selected for tracking.');
      setTrackingState('ERROR');
      return;
    }

    setGpsErrorMessage(null);
    setTrackingState('ACQUIRING');
    lastSentTimeRef.current = 0;
    lastFixTimeRef.current = null;

    // Track attempt sequence to detect cancellation or unmount while startSession is in flight
    sessionStartAttemptRef.current += 1;
    const currentAttempt = sessionStartAttemptRef.current;

    // STEP 1: Create active GPS session FIRST on backend
    let session = activeSessionRef.current;
    if (!session || !session.active || !session.id) {
      try {
        const sessionRes = await trackingService.startSession(currentAsset.id);

        // Check if user cancelled or component unmounted while startSession was in flight
        if (!isMountedRef.current || currentAttempt !== sessionStartAttemptRef.current) {
          // Immediately stop the newly created session so it doesn't linger on server
          if (sessionRes?.id) {
            try {
              await trackingService.stopSession(sessionRes.id);
            } catch (abortErr) {
              console.warn('Rollback of aborted session failed:', abortErr);
            }
          }
          return;
        }

        session = sessionRes;
        setActiveSession(sessionRes);
        activeSessionRef.current = sessionRes;
      } catch (sessionErr) {
        console.warn('Session start error:', sessionErr);
        if (!isMountedRef.current || currentAttempt !== sessionStartAttemptRef.current) {
          return;
        }

        activeSessionRef.current = null;
        setActiveSession(null);

        if (sessionErr?.response?.status === 409) {
          // 409 Conflict: session already active on server.
          // DO NOT start geolocation, DO NOT stop session, preserve the other user's session.
          const msg =
            sessionErr?.response?.data?.message ||
            'An active GPS tracking session already exists for this asset. Another user or device is currently tracking it.';
          setGpsErrorMessage(msg);
          setTrackingState('ERROR');
          return;
        } else if (sessionErr?.response?.status === 403) {
          const msg =
            sessionErr?.response?.data?.message ||
            `Access denied: You must be assigned to ${currentAsset.assetCode}, or have supervisor/administrator privileges, to start GPS tracking.`;
          setGpsErrorMessage(msg);
          setTrackingState('ERROR');
          return;
        } else {
          const msg = sessionErr?.response?.data?.message || sessionErr.message || 'Failed to start GPS tracking session on server.';
          setGpsErrorMessage(msg);
          setTrackingState('ERROR');
          return;
        }
      }
    }

    // STEP 2: Only after confirmed session exists, activate device watchPosition
    const geoOptions = {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 0,
    };

    const onGeoSuccess = async (position) => {
      if (!isMountedRef.current) return;

      // Invariant: telemetry must never be sent if session is not active
      if (!activeSessionRef.current || !activeSessionRef.current.active) {
        return;
      }

      const { latitude, longitude, accuracy, speed, heading } = position.coords;

      // Sanity checks
      if (
        latitude == null || isNaN(latitude) || latitude < -90 || latitude > 90 ||
        longitude == null || isNaN(longitude) || longitude < -180 || longitude > 180 ||
        accuracy == null || isNaN(accuracy) || accuracy < 0
      ) {
        console.warn('Discarding malformed GPS coordinate reading:', position.coords);
        return;
      }

      const now = Date.now();
      lastFixTimeRef.current = now;
      setLastUpdateAgo('Just now');

      setGpsCoords({
        latitude,
        longitude,
        accuracy,
        speed: speed != null && !isNaN(speed) ? speed : null,
        heading: heading != null && !isNaN(heading) ? heading : null,
        timestamp: position.timestamp || now,
      });

      const asset = verifiedAssetRef.current;
      if (!asset) return;

      // Transmit first fix immediately, then throttle subsequent transmissions to TELEMETRY_THROTTLE_MS
      const isFirstTelemetry = lastSentTimeRef.current === 0;
      if (isFirstTelemetry || now - lastSentTimeRef.current >= TELEMETRY_THROTTLE_MS) {
        try {
          await trackingService.recordLocation({
            assetId: asset.id,
            latitude,
            longitude,
            accuracy,
            speed: speed != null && !isNaN(speed) ? speed : null,
            heading: heading != null && !isNaN(heading) ? heading : null,
            recordedAt: new Date(now).toISOString(),
          });

          lastSentTimeRef.current = Date.now();
          setTelemetryCount((prev) => prev + 1);
          setTrackingState('ACTIVE');
        } catch (err) {
          console.warn('Telemetry transmission error:', err);
          if (isFirstTelemetry && isMountedRef.current) {
            const msg = err.response?.data?.message || err.message || 'Failed to submit telemetry to server.';
            setGpsErrorMessage(msg);
            setTrackingState('ERROR');
          }
        }
      }
    };

    const onGeoError = async (err) => {
      if (!isMountedRef.current) return;
      console.warn('Geolocation watch error:', err);

      let friendlyMsg = 'An unexpected error occurred while obtaining GPS coordinates.';
      if (err.code === 1) { // PERMISSION_DENIED
        friendlyMsg = 'Location permission was denied. Please allow device location access in your browser to start tracking.';
      } else if (err.code === 2) { // POSITION_UNAVAILABLE
        friendlyMsg = 'Your location could not be determined. Please ensure your device GPS is turned on.';
      } else if (err.code === 3) { // TIMEOUT
        friendlyMsg = 'GPS request timed out. Please ensure you have a clear view of the sky and try again.';
      }

      if (err.code === 1 || trackingStateRef.current === 'ACQUIRING') {
        setGpsErrorMessage(friendlyMsg);
        setTrackingState('ERROR');

        if (watchIdRef.current !== null) {
          try {
            navigator.geolocation.clearWatch(watchIdRef.current);
          } catch {}
          watchIdRef.current = null;
        }

        // Clean up server session ONLY if this client created/owns a confirmed session ID
        const currentSessionToStop = activeSessionRef.current;
        activeSessionRef.current = null;
        setActiveSession(null);

        if (currentSessionToStop?.id) {
          try {
            await trackingService.stopSession(currentSessionToStop.id);
          } catch (cleanupErr) {
            console.warn('Session rollback on geo error caught:', cleanupErr);
          }
        }
      }
    };

    try {
      if (watchIdRef.current !== null) {
        navigator.geolocation.clearWatch(watchIdRef.current);
      }
      const watchId = navigator.geolocation.watchPosition(onGeoSuccess, onGeoError, geoOptions);
      watchIdRef.current = watchId;
    } catch (err) {
      console.error('watchPosition invocation error:', err);
      setGpsErrorMessage('Unable to activate device geolocation: ' + err.message);
      setTrackingState('ERROR');
    }
  }, []);

  // STOP TRACKING: clears watch FIRST so no more callbacks fire, then stops server session
  const handleStopTracking = useCallback(async () => {
    sessionStartAttemptRef.current += 1;

    if (watchIdRef.current !== null && 'geolocation' in navigator) {
      try {
        navigator.geolocation.clearWatch(watchIdRef.current);
      } catch (err) {
        console.warn('clearWatch error:', err);
      }
      watchIdRef.current = null;
    }

    if (relativeTimerRef.current) {
      clearInterval(relativeTimerRef.current);
      relativeTimerRef.current = null;
    }

    const currentSession = activeSessionRef.current;
    activeSessionRef.current = null;
    setActiveSession(null);
    setTrackingState('STOPPED');

    if (currentSession?.id) {
      try {
        await trackingService.stopSession(currentSession.id);
      } catch (err) {
        if (err?.response?.status !== 404) {
          console.warn('Stop session API error:', err);
        }
      }
    }
  }, []);

  // Cancel acquisition while waiting for satellite fix
  const handleCancelAcquisition = useCallback(async () => {
    sessionStartAttemptRef.current += 1;

    if (watchIdRef.current !== null && 'geolocation' in navigator) {
      try {
        navigator.geolocation.clearWatch(watchIdRef.current);
      } catch {}
      watchIdRef.current = null;
    }

    const currentSessionToStop = activeSessionRef.current;
    activeSessionRef.current = null;
    setActiveSession(null);

    if (currentSessionToStop?.id) {
      try {
        await trackingService.stopSession(currentSessionToStop.id);
      } catch (cancelErr) {
        console.warn('Session rollback on cancel caught:', cancelErr);
      }
    }

    setTrackingState('READY');
  }, []);

  // Accuracy badge helper (High <=10m, Medium 10-30m, Low/Coarse >30m)
  const getAccuracyBadge = (acc) => {
    const category = getGpsAccuracyCategory(acc);
    if (category.category === 'UNKNOWN') return null;
    return (
      <span className={`badge ${category.badgeClass} px-2 py-0`} style={{ fontSize: '0.65rem' }}>
        {category.label}
      </span>
    );
  };

  const isScanningActive = scannerState === 'SCANNING' || scannerState === 'INITIALIZING';

  return (
    <div className="d-flex flex-column gap-3" style={{ maxWidth: '720px', margin: '0 auto' }}>
      {/* Header */}
      <div className="text-center mb-1">
        <h1 className="fs-4 fw-bold text-dark mb-1">
          {trackingState === 'ACTIVE' ? 'Live Mobile Asset Tracking' : 'Scan Medical Asset'}
        </h1>
        <p className="text-muted mb-0" style={{ fontSize: '0.85rem' }}>
          {trackingState === 'ACTIVE'
            ? 'Continuous real-time browser GPS tracking stream connected to MedTrack backend.'
            : 'Point your camera at the MedTrack asset QR code to verify and begin GPS tracking.'}
        </p>
      </div>

      {/* DEDICATED CAMERA SCANNER VIEWPORT CARD */}
      <div
        key="camera-scanner-card"
        className={`stitch-card p-3 shadow-sm ${isScanningActive && trackingState === 'READY' ? 'd-block' : 'd-none'}`}
      >
        <div
          className="position-relative overflow-hidden rounded-3 border bg-dark mx-auto d-flex align-items-center justify-content-center"
          style={{ width: '100%', maxWidth: '420px', height: '360px', minHeight: '360px' }}
        >
          {/* Mount target exclusively for Html5Qrcode video/canvas */}
          <div id="qr-reader-viewport" style={{ width: '100%', height: '100%' }} />

          {/* User-Gesture Camera Activation Fallback */}
          {scannerState === 'INITIALIZING' && (
            <button
              type="button"
              id="btn-tap-start-camera"
              className="position-absolute btn btn-primary btn-sm px-3 py-2 shadow rounded-pill"
              style={{ zIndex: 4 }}
              onClick={() => startScanner(selectedCameraId)}
            >
              <span className="material-symbols-outlined align-middle me-1 fs-6">videocam</span>
              Tap to Start Camera
            </button>
          )}

          {/* Target Reticle Overlay with Animated Laser */}
          <div
            className="position-absolute d-flex flex-column align-items-center justify-content-center pointer-events-none"
            style={{
              width: '220px',
              height: '220px',
              border: '2px solid rgba(59, 130, 246, 0.85)',
              borderRadius: '16px',
              zIndex: 2,
            }}
          >
            <div className="scanner-laser" />
            <span className="material-symbols-outlined fs-2 text-info opacity-75">qr_code_scanner</span>
            <span className="mt-2 text-white-50 text-center" style={{ fontSize: '0.7rem' }}>
              Align QR inside frame
            </span>
          </div>

          {/* Status Pill Badge */}
          <div
            className="position-absolute top-0 mt-2 px-3 py-1 rounded-pill small fw-semibold shadow-sm d-flex align-items-center gap-2"
            style={{
              backgroundColor: 'rgba(15, 23, 42, 0.85)',
              backdropFilter: 'blur(4px)',
              border: '1px solid rgba(255, 255, 255, 0.15)',
              zIndex: 3,
              fontSize: '0.725rem',
            }}
          >
            {scannerState === 'SCANNING' ? (
              <>
                <span className="spinner-grow spinner-grow-sm text-info" style={{ width: '8px', height: '8px' }} />
                <span className="text-white">Scanning...</span>
              </>
            ) : scannerState === 'DETECTED' ? (
              <>
                <span className="material-symbols-outlined fs-6 text-warning">check</span>
                <span className="text-warning">QR detected</span>
              </>
            ) : (
              <span className="text-white-50">{statusMessage}</span>
            )}
          </div>

          {/* Controls bar inside camera preview */}
          <div
            className="position-absolute bottom-0 w-100 p-2 px-3 d-flex justify-content-between align-items-center"
            style={{
              backgroundColor: 'rgba(15, 23, 42, 0.8)',
              backdropFilter: 'blur(4px)',
              zIndex: 3,
            }}
          >
            {torchAvailable ? (
              <button
                type="button"
                className={`btn btn-sm rounded-circle p-1 ${isTorchOn ? 'btn-warning' : 'btn-outline-light'}`}
                style={{ width: '32px', height: '32px' }}
                onClick={handleToggleTorch}
                title="Toggle Flashlight"
              >
                <span className="material-symbols-outlined fs-6">
                  {isTorchOn ? 'flashlight_on' : 'flashlight_off'}
                </span>
              </button>
            ) : (
              <span style={{ width: '32px' }} />
            )}

            {cameras.length > 0 && (
              <div className="d-flex align-items-center gap-1">
                <span className="material-symbols-outlined fs-6 text-white-50">videocam</span>
                <select
                  className="form-select form-select-sm py-0 px-2 bg-dark text-white border-secondary"
                  style={{ fontSize: '0.75rem', width: 'auto', maxWidth: '170px' }}
                  value={selectedCameraId || ''}
                  onChange={handleSwitchCamera}
                  id="camera-select-dropdown"
                >
                  {cameras.map((c, idx) => (
                    <option key={c.id} value={c.id}>
                      {c.label || (c.isRear ? `Rear Camera ${idx + 1}` : `Camera ${idx + 1}`)}
                    </option>
                  ))}
                </select>
              </div>
            )}

            <div className="d-flex align-items-center gap-1">
              <button
                type="button"
                className="btn btn-sm btn-outline-info rounded-circle p-1"
                style={{ width: '32px', height: '32px' }}
                onClick={runDirectGetUserMediaDiagnostic}
                title="Camera Diagnostic Probe"
                id="btn-camera-diagnostic"
              >
                <span className="material-symbols-outlined fs-6">build</span>
              </button>

              <button
                type="button"
                className="btn btn-sm btn-outline-light rounded-circle p-1"
                style={{ width: '32px', height: '32px' }}
                onClick={() => fileInputRef.current?.click()}
                title="Upload QR Image"
              >
                <span className="material-symbols-outlined fs-6">photo_library</span>
              </button>
            </div>
          </div>
        </div>

        <div className="d-flex justify-content-between align-items-center mt-2 px-1">
          <span className="text-muted" style={{ fontSize: '0.8rem' }}>
            Position camera 10–20 cm away from equipment QR code.
          </span>
          <button
            type="button"
            className="btn btn-link btn-sm p-0 text-decoration-none text-muted"
            style={{ fontSize: '0.75rem' }}
            onClick={runDirectGetUserMediaDiagnostic}
          >
            Diagnostics
          </button>
        </div>
      </div>

      {/* VERIFYING LOADING STATE */}
      {scannerState === 'VERIFYING' && (
        <div key="verifying-card" className="stitch-card p-4 shadow-sm text-center">
          <div className="spinner-border text-primary mb-2" role="status" style={{ width: '36px', height: '36px' }} />
          <h2 className="fs-5 fw-bold text-dark mb-1">Verifying Asset</h2>
          <p className="text-muted small mb-0">Checking database record for <code>{decodedValue}</code>...</p>
        </div>
      )}

      {/* ========================================================= */}
      {/* REAL GPS TRACKING ACTIVE VIEW                             */}
      {/* ========================================================= */}
      {verifiedAsset && trackingState === 'ACTIVE' && (
        <div key="tracking-active-card" className="stitch-card p-3 p-md-4 shadow-sm border border-2 border-primary">
          {/* Header Banner */}
          <div
            className="p-3 rounded-3 text-white d-flex align-items-center justify-content-between mb-3"
            style={{ backgroundColor: 'var(--primary-container)' }}
          >
            <div className="d-flex align-items-center gap-3">
              {(() => {
                const accCat = getGpsAccuracyCategory(gpsCoords.accuracy);
                return (
                  <>
                    <div
                      className={`rounded-circle ${accCat.isLowPrecision ? 'bg-warning text-dark' : 'bg-success text-white'} d-flex align-items-center justify-content-center p-2`}
                      style={{ width: '40px', height: '40px' }}
                    >
                      <span className={`spinner-grow spinner-grow-sm ${accCat.isLowPrecision ? 'text-dark' : 'text-white'}`} style={{ width: '14px', height: '14px' }} />
                    </div>
                    <div>
                      <div className="d-flex flex-wrap align-items-center gap-2 mb-1">
                        <span className="badge rounded-pill bg-white text-dark px-2 py-1 font-monospace fw-bold" style={{ fontSize: '0.7rem' }}>
                          ● SESSION ACTIVE
                        </span>
                        {accCat.category === 'UNKNOWN' ? (
                          <span className="badge rounded-pill bg-secondary text-white px-2 py-1 font-monospace" style={{ fontSize: '0.7rem' }}>
                            {accCat.label}
                          </span>
                        ) : accCat.category === 'LOW' ? (
                          <span className="badge rounded-pill bg-warning text-dark px-2 py-1 fw-bold border border-warning" style={{ fontSize: '0.7rem' }}>
                            ⚠ {accCat.label}
                          </span>
                        ) : accCat.category === 'HIGH' ? (
                          <span className="badge rounded-pill bg-success text-white px-2 py-1 fw-bold" style={{ fontSize: '0.7rem' }}>
                            ● {accCat.label}
                          </span>
                        ) : (
                          <span className="badge rounded-pill bg-info text-white px-2 py-1 fw-bold" style={{ fontSize: '0.7rem' }}>
                            ● {accCat.label}
                          </span>
                        )}
                      </div>
                      <h2 className="fs-5 fw-bold mb-0 text-white">{verifiedAsset.assetCode}</h2>
                      <div className="text-white-50" style={{ fontSize: '0.8rem' }}>{verifiedAsset.assetName}</div>
                    </div>
                  </>
                );
              })()}
            </div>

            <div className="text-end">
              <span className="badge rounded-pill bg-warning text-dark px-3 py-1 fw-bold" style={{ fontSize: '0.75rem' }}>
                MOVING
              </span>
            </div>
          </div>

          {/* Explicit Low Precision / Coarse Location Warning Alert */}
          {getGpsAccuracyCategory(gpsCoords.accuracy).isLowPrecision && (
            <div
              className="p-2 px-3 rounded-3 bg-warning-subtle text-warning-emphasis border border-warning-subtle small d-flex align-items-center gap-2 mb-3 shadow-sm"
              style={{ fontSize: '0.8rem' }}
              role="alert"
            >
              <span className="material-symbols-outlined fs-5 text-warning-emphasis flex-shrink-0">warning</span>
              <div>
                <strong>Coarse Location Fix (±{Number(gpsCoords.accuracy).toFixed(1)}m):</strong> Tracking session is active and transmitting, but the reported position has a large uncertainty radius. Moving to an open outdoor area may improve reading precision.
              </div>
            </div>
          )}

          {/* Telemetry Real-time Data Grid */}
          <div className="row g-2 mb-3">
            {/* Latitude */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100">
                <div className="text-muted small mb-1">Latitude</div>
                <div className="fs-5 fw-bold text-dark font-monospace" id="gps-display-latitude">
                  {gpsCoords.latitude != null ? gpsCoords.latitude.toFixed(6) : '—'}
                </div>
                <div className="text-muted" style={{ fontSize: '0.7rem' }}>Decimal Degrees</div>
              </div>
            </div>

            {/* Longitude */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100">
                <div className="text-muted small mb-1">Longitude</div>
                <div className="fs-5 fw-bold text-dark font-monospace" id="gps-display-longitude">
                  {gpsCoords.longitude != null ? gpsCoords.longitude.toFixed(6) : '—'}
                </div>
                <div className="text-muted" style={{ fontSize: '0.7rem' }}>Decimal Degrees</div>
              </div>
            </div>

            {/* Accuracy */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small mb-1">Accuracy</div>
                  <div className="fs-5 fw-bold text-dark font-monospace" id="gps-display-accuracy">
                    {gpsCoords.accuracy != null ? `${gpsCoords.accuracy.toFixed(1)} m` : '—'}
                  </div>
                </div>
                <div className="mt-1">{getAccuracyBadge(gpsCoords.accuracy)}</div>
              </div>
            </div>

            {/* Speed */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small mb-1">Speed</div>
                  <div className="fs-5 fw-bold text-dark font-monospace" id="gps-display-speed">
                    {gpsCoords.speed != null ? `${gpsCoords.speed.toFixed(1)} m/s` : '0.0 m/s'}
                  </div>
                </div>
                <div className="text-muted small" style={{ fontSize: '0.7rem' }}>
                  {gpsCoords.speed != null && gpsCoords.speed > 0.3 ? 'In Motion' : 'Stationary'}
                </div>
              </div>
            </div>

            {/* Heading */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100">
                <div className="text-muted small mb-1">Heading</div>
                <div className="fs-5 fw-bold text-dark font-monospace" id="gps-display-heading">
                  {gpsCoords.heading != null ? `${gpsCoords.heading.toFixed(0)}°` : 'N/A'}
                </div>
                <div className="text-muted" style={{ fontSize: '0.7rem' }}>Orientation</div>
              </div>
            </div>

            {/* Last Update */}
            <div className="col-6">
              <div className="p-3 rounded-3 bg-light border text-center h-100 d-flex flex-column justify-content-between">
                <div>
                  <div className="text-muted small mb-1">Last Update</div>
                  <div className="fs-6 fw-bold text-primary" id="gps-display-last-update">{lastUpdateAgo}</div>
                </div>
                <div className="badge bg-secondary-subtle text-secondary small px-2 py-1 mx-auto" style={{ fontSize: '0.65rem' }}>
                  {telemetryCount} transmissions
                </div>
              </div>
            </div>
          </div>

          {/* Info callout */}
          <div className="p-2 px-3 rounded-3 bg-primary-subtle text-primary small d-flex align-items-center gap-2 mb-3" style={{ fontSize: '0.75rem' }}>
            <span className="material-symbols-outlined fs-6">sync</span>
            <span>
              Real mobile geolocation stream active. Telemetry updates persist to MySQL <code>location_history</code> every ~5 seconds.
            </span>
          </div>

          {/* Action Buttons: VIEW ON LIVE MAP & STOP TRACKING */}
          <div className="d-flex flex-column flex-sm-row gap-2">
            <button
              type="button"
              className="btn btn-outline-primary flex-grow-1 py-3 d-flex align-items-center justify-content-center gap-2 shadow-sm"
              onClick={() => window.open(`/live-tracking?assetId=${verifiedAsset.id}`, '_blank')}
              id="btn-view-live-map"
              title="Open Live Tracking map in a new tab to observe live marker position"
            >
              <span className="material-symbols-outlined fs-5">open_in_new</span>
              <span className="fw-bold fs-6">VIEW ON LIVE MAP</span>
            </button>

            <button
              type="button"
              className="btn btn-danger flex-grow-1 py-3 d-flex align-items-center justify-content-center gap-2 shadow-sm"
              onClick={handleStopTracking}
              id="btn-stop-tracking"
            >
              <span className="material-symbols-outlined fs-5">stop_circle</span>
              <span className="fw-bold fs-6">STOP TRACKING</span>
            </button>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* ACQUIRING GPS LOADING STATE                               */}
      {/* ========================================================= */}
      {verifiedAsset && trackingState === 'ACQUIRING' && (
        <div key="acquiring-gps-card" className="stitch-card p-4 shadow-sm text-center">
          <div className="spinner-border text-primary mb-3" role="status" style={{ width: '48px', height: '48px' }} />
          <h2 className="fs-5 fw-bold text-dark mb-1">Acquiring GPS...</h2>
          <p className="text-muted small mb-3" style={{ maxWidth: '440px', margin: '0 auto' }}>
            Requesting browser geolocation sensor permissions and waiting for satellite fix for <strong>{verifiedAsset.assetCode}</strong>.
          </p>
          <div className="p-2 px-3 rounded-3 bg-light border text-muted small mx-auto mb-3" style={{ maxWidth: '380px', fontSize: '0.75rem' }}>
            Please tap <strong>Allow</strong> if your browser prompts for device location access.
          </div>
          <button
            type="button"
            className="btn btn-outline-secondary btn-sm px-4"
            onClick={handleCancelAcquisition}
          >
            Cancel
          </button>
        </div>
      )}

      {/* ========================================================= */}
      {/* GPS ERROR STATE                                           */}
      {/* ========================================================= */}
      {verifiedAsset && trackingState === 'ERROR' && (
        <div key="gps-error-card" className="stitch-card p-4 shadow-sm text-center">
          <div className="d-inline-flex align-items-center justify-content-center p-3 rounded-circle bg-danger-subtle text-danger mb-2">
            <span className="material-symbols-outlined fs-2">location_off</span>
          </div>
          <h2 className="fs-5 fw-bold text-dark mb-1">GPS Location Error</h2>
          <p className="text-muted small mb-3" style={{ maxWidth: '460px', margin: '0 auto' }}>
            {gpsErrorMessage || 'Unable to acquire real GPS coordinates from device.'}
          </p>
          <div className="d-flex justify-content-center gap-2">
            <button
              type="button"
              className="btn btn-stitch-primary px-3 py-2 btn-sm"
              onClick={handleStartTracking}
              id="btn-retry-gps"
            >
              <span className="material-symbols-outlined fs-5 me-1">refresh</span>
              <span>Retry GPS</span>
            </button>
            <button
              type="button"
              className="btn btn-outline-secondary px-3 py-2 btn-sm"
              onClick={() => setTrackingState('READY')}
            >
              Back to Asset
            </button>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* TRACKING STOPPED STATE                                    */}
      {/* ========================================================= */}
      {verifiedAsset && trackingState === 'STOPPED' && (
        <div key="tracking-stopped-card" className="stitch-card p-3 p-md-4 shadow-sm">
          <div className="d-flex align-items-center gap-2 mb-3">
            <div className="p-2 rounded-circle bg-warning-subtle text-warning d-flex align-items-center justify-content-center">
              <span className="material-symbols-outlined fs-5">pause_circle</span>
            </div>
            <div>
              <h2 className="fs-5 fw-bold mb-0 text-dark">Tracking Stopped</h2>
              <div className="text-muted small">GPS telemetry recording has ceased for {verifiedAsset.assetCode}</div>
            </div>
          </div>

          <div className="p-3 bg-light rounded-3 border mb-3" style={{ fontSize: '0.825rem' }}>
            <div className="row g-2">
              <div className="col-6">
                <div className="text-muted small">Total Coordinates Logged</div>
                <div className="fw-bold text-dark">{telemetryCount} points</div>
              </div>
              <div className="col-6">
                <div className="text-muted small">Final Status</div>
                <div className="fw-bold text-dark">Available / In Storage</div>
              </div>
              {gpsCoords.latitude != null && (
                <div className="col-12 border-top pt-2">
                  <div className="text-muted small">Last Known Coordinates</div>
                  <div className="fw-bold text-dark font-monospace">
                    {gpsCoords.latitude.toFixed(6)}, {gpsCoords.longitude.toFixed(6)} (±{gpsCoords.accuracy?.toFixed(1)} m)
                  </div>
                </div>
              )}
            </div>
          </div>

          <div className="d-flex flex-column flex-sm-row gap-2">
            <button
              type="button"
              className="btn btn-stitch-primary flex-grow-1 py-2 d-flex align-items-center justify-content-center gap-2"
              onClick={handleStartTracking}
              id="btn-restart-tracking"
            >
              <span className="material-symbols-outlined fs-5">play_arrow</span>
              <span className="fw-semibold">START TRACKING AGAIN</span>
            </button>

            <button
              type="button"
              className="btn btn-outline-primary py-2 d-flex align-items-center justify-content-center gap-2"
              onClick={() => navigate(`/live-tracking?assetId=${verifiedAsset.id}`)}
              id="btn-view-map-stopped"
            >
              <span className="material-symbols-outlined fs-5">map</span>
              <span className="fw-semibold">VIEW ON MAP</span>
            </button>

            <button
              type="button"
              className="btn btn-outline-secondary py-2 d-flex align-items-center justify-content-center gap-2"
              onClick={handleRestartScanner}
              id="btn-scan-another-stopped"
            >
              <span className="material-symbols-outlined fs-5">qr_code_scanner</span>
              <span className="fw-semibold">SCAN ANOTHER</span>
            </button>
          </div>
        </div>
      )}

      {/* ========================================================= */}
      {/* READY STATE: ASSET VERIFIED (NO TRACKING ACTIVE YET)      */}
      {/* ========================================================= */}
      {verifiedAsset && trackingState === 'READY' && (
        <div key="asset-verified-card" className="stitch-card p-3 p-md-4 shadow-sm">
          {/* Header Banner */}
          <div
            className="p-3 rounded-3 text-white d-flex align-items-center justify-content-between mb-3"
            style={{ backgroundColor: 'var(--primary-container)' }}
          >
            <div className="d-flex align-items-center gap-3">
              <div
                className="rounded-circle bg-success text-white d-flex align-items-center justify-content-center p-2"
                style={{ width: '40px', height: '40px' }}
              >
                <span className="material-symbols-outlined fs-5">check_circle</span>
              </div>
              <div>
                <span className="badge rounded-pill bg-success-subtle text-success px-2 py-1 mb-1" style={{ fontSize: '0.7rem' }}>
                  Asset Verified
                </span>
                <h2 className="fs-5 fw-bold mb-0 text-white">{verifiedAsset.assetCode}</h2>
                <div className="text-white-50" style={{ fontSize: '0.8rem' }}>{verifiedAsset.assetName}</div>
              </div>
            </div>

            <div className="text-end">
              <span className="badge rounded-pill bg-white text-dark px-3 py-1 fw-bold" style={{ fontSize: '0.75rem' }}>
                {verifiedAsset.status}
              </span>
            </div>
          </div>

          {/* Specifications Grid */}
          <div className="row g-2 p-3 rounded-3 bg-light mb-3" style={{ fontSize: '0.825rem' }}>
            <div className="col-6 col-md-4">
              <div className="text-muted small">Category</div>
              <div className="fw-semibold text-dark">{verifiedAsset.category || 'Not available'}</div>
            </div>
            <div className="col-6 col-md-4">
              <div className="text-muted small">Department</div>
              <div className="fw-semibold text-dark">{verifiedAsset.departmentName || 'Not available'}</div>
            </div>
            <div className="col-6 col-md-4">
              <div className="text-muted small">Status</div>
              <div className="fw-semibold text-dark">{verifiedAsset.status}</div>
            </div>
            <div className="col-6 col-md-4">
              <div className="text-muted small">Serial Number</div>
              <div className="fw-semibold text-dark font-monospace">{verifiedAsset.serialNumber || 'Not available'}</div>
            </div>
            <div className="col-6 col-md-4">
              <div className="text-muted small">QR Status</div>
              <div className="fw-semibold text-success d-flex align-items-center gap-1">
                <span className="material-symbols-outlined fs-6">verified</span>
                <span>Verified</span>
              </div>
            </div>
            <div className="col-6 col-md-4">
              <div className="text-muted small">Battery Level</div>
              <div className="fw-semibold text-dark">
                {verifiedAsset.batteryLevel != null ? `${verifiedAsset.batteryLevel}%` : 'Not available'}
              </div>
            </div>
            <div className="col-12 border-top pt-2 mt-1">
              <div className="d-flex flex-wrap justify-content-between align-items-center gap-1">
                <div>
                  <div className="text-muted small">Last Known GPS Location</div>
                  <div className="fw-semibold text-secondary font-monospace" style={{ fontSize: '0.8rem' }}>
                    {verifiedAsset.latitude != null && verifiedAsset.longitude != null
                      ? `${verifiedAsset.latitude.toFixed(6)}, ${verifiedAsset.longitude.toFixed(6)}`
                      : 'No previous GPS telemetry recorded'}
                  </div>
                </div>
                <div className="d-flex align-items-center gap-1">
                  {isAdminOrSupervisor ? (
                    <span className="badge bg-success-subtle text-success small px-2 py-1" style={{ fontSize: '0.7rem' }}>
                      <span className="material-symbols-outlined align-middle fs-6 me-1">verified_user</span>
                      Supervisor/Admin Authorized
                    </span>
                  ) : (
                    <span className="badge bg-primary-subtle text-primary small px-2 py-1" style={{ fontSize: '0.7rem' }}>
                      <span className="material-symbols-outlined align-middle fs-6 me-1">badge</span>
                      Staff Assignment Mode
                    </span>
                  )}
                </div>
              </div>
            </div>
          </div>

          {/* Custody & Transfer Context (Phase 17) */}
          <div className="p-3 rounded-3 border mb-3" style={{ backgroundColor: 'var(--surface-container-low)', fontSize: '0.825rem' }}>
            <div className="d-flex align-items-center justify-content-between mb-2">
              <div className="d-flex align-items-center gap-1 fw-bold text-dark">
                <span className="material-symbols-outlined fs-5 text-primary">swap_horiz</span>
                <span>Custody &amp; Transfer Context</span>
              </div>
              {activeTransfer ? (
                <span className="badge bg-warning-subtle text-dark border border-warning px-2 py-1">
                  Transfer #{activeTransfer.id}: {activeTransfer.status}
                </span>
              ) : (
                <span className="badge bg-success-subtle text-success border border-success px-2 py-1">
                  No Active Transfer
                </span>
              )}
            </div>

            <div className="row g-2">
              <div className="col-6">
                <div className="text-muted small">Current Department</div>
                <div className="fw-semibold text-dark">{verifiedAsset.departmentName || 'General Inventory'}</div>
              </div>
              <div className="col-6">
                <div className="text-muted small">Current Custody Status</div>
                <div className="fw-semibold text-dark">{verifiedAsset.status}</div>
              </div>
              {activeTransfer && (
                <>
                  <div className="col-6 border-top pt-2">
                    <div className="text-muted small">Target Department</div>
                    <div className="fw-semibold text-primary">{activeTransfer.destinationDepartmentName}</div>
                  </div>
                  <div className="col-6 border-top pt-2">
                    <div className="text-muted small">Target Assignee</div>
                    <div className="fw-semibold text-dark">{activeTransfer.destinationUserName || 'Department General Pool'}</div>
                  </div>
                  <div className="col-12 border-top pt-1 text-muted small">
                    Requested by {activeTransfer.requestedByName} (Status: <strong>{activeTransfer.status}</strong>)
                  </div>
                </>
              )}
            </div>

            <div className="mt-2 pt-2 border-top d-flex justify-content-end">
              <button
                type="button"
                className="btn btn-sm btn-outline-primary d-inline-flex align-items-center gap-1"
                onClick={() => navigate('/transfers', { state: { targetAsset: verifiedAsset } })}
                style={{ fontSize: '0.775rem' }}
                id="btn-navigate-transfers"
              >
                <span className="material-symbols-outlined fs-6">sync_alt</span>
                <span>{activeTransfer ? 'Manage Active Transfer' : 'Initiate Equipment Transfer'}</span>
              </button>
            </div>
          </div>

          {/* Action Buttons Row */}
          <div className="d-flex flex-column flex-sm-row gap-2">
            <button
              type="button"
              className="btn btn-stitch-primary flex-grow-1 py-2 d-flex align-items-center justify-content-center gap-2"
              onClick={handleStartTracking}
              id="btn-start-tracking"
            >
              <span className="material-symbols-outlined fs-5">play_arrow</span>
              <span className="fw-semibold">START TRACKING</span>
            </button>

            <button
              type="button"
              className="btn btn-outline-secondary py-2 d-flex align-items-center justify-content-center gap-2"
              onClick={handleRestartScanner}
              id="btn-scan-another"
            >
              <span className="material-symbols-outlined fs-5">qr_code_scanner</span>
              <span className="fw-semibold">SCAN ANOTHER</span>
            </button>

            {onSelectAsset && (
              <button
                type="button"
                className="btn btn-light border py-2 d-flex align-items-center justify-content-center gap-1"
                onClick={() => onSelectAsset(verifiedAsset)}
                title="Inspect full asset record"
                id="btn-view-details"
              >
                <span className="material-symbols-outlined fs-5">visibility</span>
                <span>Details</span>
              </button>
            )}
          </div>
        </div>
      )}

      {/* UNRECOGNIZED QR STATE */}
      {scannerState === 'NOT_FOUND' && (
        <div key="qr-not-found-card" className="stitch-card p-4 shadow-sm text-center">
          <div className="d-inline-flex align-items-center justify-content-center p-3 rounded-circle bg-danger-subtle text-danger mb-2">
            <span className="material-symbols-outlined fs-2">error_outline</span>
          </div>
          <h2 className="fs-5 fw-bold text-dark mb-1">QR Not Recognized</h2>
          <p className="text-muted small mb-3" style={{ maxWidth: '440px', margin: '0 auto' }}>
            This QR code is not registered in MedTrack.
          </p>
          {decodedValue && (
            <div className="p-2 px-3 rounded-3 bg-light text-break font-monospace small mb-3 mx-auto" style={{ maxWidth: '440px' }}>
              <span className="text-muted">Scanned Value: </span>
              <span className="text-dark fw-bold">{decodedValue}</span>
            </div>
          )}
          <button
            type="button"
            className="btn btn-stitch-primary px-4 py-2"
            onClick={handleRestartScanner}
            id="btn-try-again"
          >
            <span className="material-symbols-outlined fs-5 me-1">refresh</span>
            <span>TRY AGAIN</span>
          </button>
        </div>
      )}

      {/* CAMERA ERROR STATE */}
      {scannerState === 'ERROR' && (
        <div key="camera-error-card" className="stitch-card p-4 shadow-sm text-center">
          <div className="d-inline-flex align-items-center justify-content-center p-3 rounded-circle bg-warning-subtle text-warning mb-2">
            <span className="material-symbols-outlined fs-2">videocam_off</span>
          </div>
          <h2 className="fs-5 fw-bold text-dark mb-1">Camera Unavailable</h2>
          <p className="text-muted small mb-3" style={{ maxWidth: '480px', margin: '0 auto' }}>
            {errorMessage || 'Camera access is required to scan an asset QR code.'}
          </p>
          <div className="d-flex flex-wrap justify-content-center gap-2">
            <button
              type="button"
              className="btn btn-stitch-primary px-3 py-2 btn-sm"
              onClick={() => startScanner(selectedCameraId)}
              id="btn-retry-camera"
            >
              <span className="material-symbols-outlined fs-5 me-1">refresh</span>
              <span>Retry Camera</span>
            </button>

            <button
              type="button"
              className="btn btn-outline-secondary px-3 py-2 btn-sm"
              onClick={() => fileInputRef.current?.click()}
            >
              <span className="material-symbols-outlined fs-5 me-1">upload_file</span>
              <span>Upload QR Image</span>
            </button>
          </div>
        </div>
      )}

      {/* Hidden File Input for scanning QR image file */}
      <input
        ref={fileInputRef}
        type="file"
        accept="image/*"
        className="d-none"
        onChange={handleFileUpload}
      />

      {/* Manual Code Input & Test Helpers Row */}
      <div className="row g-3">
        {/* Manual Lookup Form */}
        <div className="col-12 col-md-7">
          <div className="stitch-card p-3 h-100">
            <h3 className="fs-6 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
              <span className="material-symbols-outlined fs-5 text-primary">keyboard</span>
              <span>Manual Asset Code / QR Entry</span>
            </h3>
            <p className="text-muted small mb-2" style={{ fontSize: '0.8rem' }}>
              If camera scanning is unavailable, enter the asset code or full QR string.
            </p>
            <form onSubmit={handleManualSubmit}>
              <div className="input-group input-group-sm">
                <input
                  type="text"
                  className="form-control"
                  placeholder="e.g. ECG-001 or MEDTRACK:ASSET:ECG-001"
                  value={manualCode}
                  onChange={(e) => setManualCode(e.target.value)}
                  id="input-manual-qr"
                />
                <button
                  type="submit"
                  className="btn btn-stitch-primary px-3"
                  id="btn-manual-verify"
                  disabled={!manualCode.trim() || scannerState === 'VERIFYING'}
                >
                  Verify
                </button>
              </div>
            </form>
          </div>
        </div>

        {/* Physical QR Test Helper Drawer */}
        <div className="col-12 col-md-5">
          <div className="stitch-card p-3 h-100 d-flex flex-column justify-content-between bg-light">
            <div>
              <h3 className="fs-6 fw-bold text-dark mb-1 d-flex align-items-center gap-2">
                <span className="material-symbols-outlined fs-5 text-success">qr_code_2</span>
                <span>Physical QR Testing</span>
              </h3>
              <p className="text-muted small mb-2" style={{ fontSize: '0.8rem' }}>
                Display sample QR codes on another screen to test physical phone scanning.
              </p>
            </div>
            <button
              type="button"
              className="btn btn-outline-primary btn-sm w-100 d-flex align-items-center justify-content-center gap-2"
              onClick={() => setShowSamplesModal(true)}
              id="btn-open-sample-qrs"
            >
              <span className="material-symbols-outlined fs-6">visibility</span>
              <span className="fw-semibold">Show Test QR Codes</span>
            </button>
          </div>
        </div>
      </div>

      {/* SAMPLE QR CODES MODAL (For Physical Testing) */}
      {showSamplesModal && (
        <div
          className="modal fade show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.7)', backdropFilter: 'blur(4px)', zIndex: 1060 }}
        >
          <div className="modal-dialog modal-dialog-centered modal-lg">
            <div className="modal-content stitch-card p-0 border-0 shadow-lg overflow-hidden">
              <div
                className="p-3 px-4 d-flex align-items-center justify-content-between text-white"
                style={{ backgroundColor: 'var(--primary-container)' }}
              >
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined fs-4">qr_code_scanner</span>
                  <h3 className="fs-5 fw-bold mb-0 text-white">Physical QR Verification Codes</h3>
                </div>
                <button
                  type="button"
                  className="btn btn-sm btn-outline-light rounded-circle p-1"
                  onClick={() => setShowSamplesModal(false)}
                >
                  <span className="material-symbols-outlined fs-6">close</span>
                </button>
              </div>

              <div className="p-3 p-md-4 bg-white">
                <p className="text-muted small mb-3">
                  Open this page on a desktop or secondary screen, and point your mobile phone camera at any QR code below to verify real camera decoding:
                </p>

                <div className="row g-3">
                  {SAMPLE_QRS.map((sample) => (
                    <div key={sample.code} className="col-12 col-sm-6 col-lg-3">
                      <div className="p-2 rounded-3 border text-center h-100 d-flex flex-column align-items-center justify-content-between bg-light">
                        <div className="bg-white p-2 rounded-3 border mb-2 shadow-sm">
                          <img
                            src={sample.file}
                            alt={sample.qr}
                            style={{ width: '120px', height: '120px', objectFit: 'contain' }}
                          />
                        </div>
                        <div>
                          <div className="fw-bold text-dark" style={{ fontSize: '0.825rem' }}>{sample.code}</div>
                          <div className="text-muted small" style={{ fontSize: '0.725rem' }}>{sample.name}</div>
                          <code className="text-primary d-block mt-1" style={{ fontSize: '0.625rem' }}>{sample.qr}</code>
                        </div>
                        <button
                          type="button"
                          className="btn btn-sm btn-outline-secondary mt-2 w-100"
                          style={{ fontSize: '0.7rem' }}
                          onClick={async () => {
                            setShowSamplesModal(false);
                            await verifyQrCode(sample.qr);
                          }}
                        >
                          Test In Scanner
                        </button>
                      </div>
                    </div>
                  ))}
                </div>

                <div className="mt-3 p-2 px-3 rounded-3 bg-secondary-subtle text-secondary small d-flex align-items-center gap-2" style={{ fontSize: '0.75rem' }}>
                  <span className="material-symbols-outlined fs-6">info</span>
                  <span>
                    MedTrack QR Format: <code>MEDTRACK:ASSET:&lt;ASSET_CODE&gt;</code>. These identifiers query <code>medtrack_db.assets</code> directly.
                  </span>
                </div>
              </div>

              <div className="p-3 px-4 bg-light border-top text-end">
                <button
                  type="button"
                  className="btn btn-secondary btn-sm px-4"
                  onClick={() => setShowSamplesModal(false)}
                >
                  Close
                </button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* DIRECT GETUSERMEDIA DIAGNOSTIC MODAL */}
      {showDiagnosticModal && (
        <div
          className="modal fade show d-block"
          tabIndex="-1"
          style={{ backgroundColor: 'rgba(15, 23, 42, 0.75)', backdropFilter: 'blur(4px)', zIndex: 1070 }}
        >
          <div className="modal-dialog modal-dialog-centered" style={{ maxWidth: '480px' }}>
            <div className="modal-content stitch-card p-0 border-0 shadow-lg overflow-hidden">
              <div className="p-3 bg-dark text-white d-flex align-items-center justify-content-between">
                <div className="d-flex align-items-center gap-2">
                  <span className="material-symbols-outlined text-info">biotech</span>
                  <span className="fw-bold">Camera Diagnostic Probe</span>
                </div>
                <button
                  type="button"
                  className="btn-close btn-close-white"
                  onClick={closeDiagnosticModal}
                  aria-label="Close"
                />
              </div>

              <div className="p-3">
                <p className="text-muted small mb-3">
                  Direct <code>navigator.mediaDevices.getUserMedia</code> probe to verify mobile camera feed on Android Chrome &amp; iOS Safari.
                </p>

                {/* Direct Video Preview Container */}
                <div
                  className="position-relative overflow-hidden rounded-3 border bg-black mb-3 mx-auto"
                  style={{ width: '100%', height: '220px' }}
                >
                  <video
                    id="diagnostic-preview-video"
                    playsInline
                    webkit-playsinline="true"
                    muted
                    autoPlay
                    style={{ width: '100%', height: '100%', objectFit: 'cover', display: 'block' }}
                  />
                  {diagnosticLoading && (
                    <div className="position-absolute top-50 start-50 translate-middle text-white text-center">
                      <div className="spinner-border spinner-border-sm text-info mb-1" />
                      <div className="small">Probing hardware stream...</div>
                    </div>
                  )}
                </div>

                {/* Probe Results */}
                {diagnosticResult && (
                  <div className="p-2 px-3 rounded-3 bg-light border small font-monospace mb-3">
                    <div className="d-flex justify-content-between mb-1">
                      <span className="text-muted">Direct getUserMedia:</span>
                      <span className={diagnosticResult.success ? 'text-success fw-bold' : 'text-danger fw-bold'}>
                        {diagnosticResult.success ? 'PASS (Stream Active)' : 'FAIL'}
                      </span>
                    </div>
                    {diagnosticResult.success ? (
                      <>
                        <div className="d-flex justify-content-between mb-1">
                          <span className="text-muted">Track Label:</span>
                          <span className="text-dark text-truncate" style={{ maxWidth: '240px' }}>
                            {diagnosticResult.trackLabel}
                          </span>
                        </div>
                        <div className="d-flex justify-content-between mb-1">
                          <span className="text-muted">Facing Mode:</span>
                          <span className="text-dark">{diagnosticResult.facingMode}</span>
                        </div>
                        <div className="d-flex justify-content-between mb-1">
                          <span className="text-muted">Video Dimensions:</span>
                          <span className="text-dark fw-bold">
                            {diagnosticResult.actualVideoWidth
                              ? `${diagnosticResult.actualVideoWidth} x ${diagnosticResult.actualVideoHeight}`
                              : `${diagnosticResult.settingsWidth} x ${diagnosticResult.settingsHeight}`}
                          </span>
                        </div>
                        <div className="d-flex justify-content-between">
                          <span className="text-muted">Ready State:</span>
                          <span className="text-success fw-bold">
                            {diagnosticResult.videoReadyState != null
                              ? `State ${diagnosticResult.videoReadyState} (HAVE_ENOUGH_DATA)`
                              : diagnosticResult.readyState}
                          </span>
                        </div>
                      </>
                    ) : (
                      <div className="text-danger small mt-1">
                        <strong>Error:</strong> {diagnosticResult.errorMessage}
                      </div>
                    )}
                  </div>
                )}

                <div className="d-flex gap-2">
                  <button
                    type="button"
                    className="btn btn-outline-primary btn-sm flex-grow-1"
                    onClick={runDirectGetUserMediaDiagnostic}
                    disabled={diagnosticLoading}
                  >
                    Re-probe Hardware
                  </button>
                  <button
                    type="button"
                    className="btn btn-stitch-primary btn-sm px-4"
                    onClick={() => {
                      closeDiagnosticModal();
                      startScanner(selectedCameraId);
                    }}
                  >
                    Resume Scanner
                  </button>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
