export const mockAssets = [
  {
    id: 'ECG-023',
    name: 'CardioScan 500',
    category: 'Monitoring Equipment',
    department: 'Cardiology',
    status: 'in-use',
    statusLabel: 'In Use',
    location: 'ICU Bay 4',
    floor: 'Floor 3',
    lastUpdated: '2 mins ago',
    battery: 84,
    coords: { top: '30%', left: '25%' },
    serialNumber: 'SN-CARD-99281',
    beaconId: 'BCN-CRD-09',
    assignedStaff: 'Dr. Sarah Lin (Cardio Lead)',
    lastMaintenance: '2026-08-15',
    telemetrySpeed: '0.4 m/s (Moving)',
    notes: 'Calibrated for telemetry pulse synchronization.'
  },
  {
    id: 'Pump-014',
    name: 'InfusionPro V2',
    category: 'Infusion Pumps',
    department: 'Emergency Room',
    status: 'active',
    statusLabel: 'Active',
    location: 'ER Trauma 1',
    floor: 'Floor 3',
    lastUpdated: '14 mins ago',
    battery: 96,
    coords: { top: '60%', left: '45%' },
    serialNumber: 'SN-PUMP-44102',
    beaconId: 'BCN-ER-22',
    assignedStaff: 'Nurse Mark Davies',
    lastMaintenance: '2026-09-01',
    telemetrySpeed: '0.0 m/s (Stationary)',
    notes: 'Continuous intravenous drip dosage monitor active.'
  },
  {
    id: 'Ventilator-003',
    name: 'RespiraAir Elite',
    category: 'Respiratory',
    department: 'ICU - Intensive Care',
    status: 'maintenance',
    statusLabel: 'Maintenance',
    location: 'Biomedical Workshop',
    floor: 'Floor 2',
    lastUpdated: '1 hour ago',
    battery: 54,
    coords: { top: '42%', left: '60%' },
    serialNumber: 'SN-VENT-11003',
    beaconId: 'BCN-ICU-03',
    assignedStaff: 'BioMed Tech Kevin Vance',
    lastMaintenance: '2026-09-27',
    telemetrySpeed: '0.0 m/s (Offline test)',
    notes: 'Scheduled 6-month turbine filter calibration in progress.'
  },
  {
    id: 'Monitor-021',
    name: 'VeloVitalis 360',
    category: 'Patient Monitors',
    department: 'Pediatrics',
    status: 'error',
    statusLabel: 'Low Battery',
    location: 'Peds Ward B',
    floor: 'Floor 3',
    lastUpdated: '35 mins ago',
    battery: 4,
    coords: { top: '25%', left: '70%' },
    serialNumber: 'SN-MON-78219',
    beaconId: 'BCN-PEDS-14',
    assignedStaff: 'Nurse Angela Gomez',
    lastMaintenance: '2026-07-20',
    telemetrySpeed: '0.0 m/s (Stationary)',
    notes: 'Battery dropped below 5%. Return to charging dock immediately.'
  },
  {
    id: 'Wheelchair-008',
    name: 'ErgoRoll Pro',
    category: 'Mobility Equipment',
    department: 'General Ward',
    status: 'active',
    statusLabel: 'Active',
    location: 'Lobby B',
    floor: 'Floor 2',
    lastUpdated: '12 mins ago',
    battery: 100,
    coords: { top: '75%', left: '80%' },
    serialNumber: 'SN-WCH-88008',
    beaconId: 'BCN-LBY-08',
    assignedStaff: 'Transport Orderly T. Scott',
    lastMaintenance: '2026-06-11',
    telemetrySpeed: '1.1 m/s (Moving)',
    notes: 'Available for patient transit at Main Lobby East.'
  },
  {
    id: 'Defibrillator-04',
    name: 'HeartStart XL+',
    category: 'Emergency Resuscitation',
    department: 'Emergency Room',
    status: 'active',
    statusLabel: 'Active',
    location: 'ER Bay 2',
    floor: 'Floor 3',
    lastUpdated: '5 mins ago',
    battery: 92,
    coords: { top: '52%', left: '35%' },
    serialNumber: 'SN-DEF-32049',
    beaconId: 'BCN-ER-04',
    assignedStaff: 'Dr. James Chen (ER Lead)',
    lastMaintenance: '2026-09-10',
    telemetrySpeed: '0.0 m/s (Docked)',
    notes: 'Crash cart standby unit in optimal diagnostic state.'
  }
];

export const signatureMetrics = [
  {
    id: 'movement-continuity',
    title: 'Asset Movement Continuity',
    value: '91%',
    delta: '+2.4% vs last week',
    deltaPositive: true,
    progress: 91,
    color: 'var(--primary)',
    icon: 'analytics',
    description: 'Measures continuous uptime and telemetry tracking across hospital pathways.'
  },
  {
    id: 'handoff-friction',
    title: 'Handoff Friction Index',
    value: '18',
    delta: '-5 pts (Improved)',
    deltaPositive: true,
    progress: 35,
    color: 'var(--tertiary)',
    icon: 'swap_horiz',
    description: 'Average department custody turnaround delay; lower index denotes fluid handover.'
  },
  {
    id: 'geo-utilization',
    title: 'Geo-Utilization Balance',
    value: '82%',
    delta: 'Optimal spread',
    deltaPositive: true,
    progress: 82,
    color: 'var(--secondary)',
    icon: 'explore',
    description: 'Spatial distribution efficiency preventing device hoarding in acute wards.'
  }
];

export const kpiSummary = [
  {
    id: 'total-assets',
    title: 'Total Assets',
    value: '42',
    delta: '+4% this month',
    icon: 'inventory_2',
    colorClass: 'text-primary',
    bgClass: 'bg-primary-subtle'
  },
  {
    id: 'moving-assets',
    title: 'Moving Assets',
    value: '7',
    delta: 'In transit now',
    icon: 'local_shipping',
    colorClass: 'text-success',
    bgClass: 'bg-success-subtle'
  },
  {
    id: 'active-alerts',
    title: 'Active Alerts',
    value: '3',
    delta: 'Requires attention',
    icon: 'notifications_active',
    colorClass: 'text-danger',
    bgClass: 'bg-danger-subtle'
  },
  {
    id: 'offline-assets',
    title: 'Offline Assets',
    value: '2',
    delta: 'Low priority',
    icon: 'signal_wifi_off',
    colorClass: 'text-secondary',
    bgClass: 'bg-light'
  }
];

export const mockAlerts = [
  {
    id: 'ALT-101',
    assetId: 'Monitor-021',
    level: 'critical',
    title: 'Battery level critical (4%) in ER Bay 3',
    time: '12m ago',
    icon: 'battery_alert',
    department: 'Pediatrics'
  },
  {
    id: 'ALT-102',
    assetId: 'Defibrillator-04',
    level: 'critical',
    title: 'Outside geo-fence boundary (North Wing)',
    time: '45m ago',
    icon: 'gpp_maybe',
    department: 'Emergency'
  },
  {
    id: 'ALT-103',
    assetId: 'Infusion Pump-012',
    level: 'warning',
    title: 'Maintenance due check pending',
    time: '2h ago',
    icon: 'schedule',
    department: 'General'
  }
];

export const mockTimeline = [
  {
    id: 'TL-1',
    time: '10:42 AM',
    title: 'ECG-023 Transferred',
    desc: 'Moved from Storage B to Cardiology Ward by Nurse R. Miller.',
    color: 'var(--primary)'
  },
  {
    id: 'TL-2',
    time: '10:15 AM',
    title: 'Pump-014 Docked',
    desc: 'Stationary charging initiated at ICU Nursing Station 2.',
    color: 'var(--secondary)'
  },
  {
    id: 'TL-3',
    time: '09:50 AM',
    title: 'Wheelchair-008 Released',
    desc: 'Checked out at Main Lobby East entrance kiosk.',
    color: 'var(--tertiary)'
  },
  {
    id: 'TL-4',
    time: '09:12 AM',
    title: 'Alert Triggered',
    desc: 'Monitor-021 battery dropped below critical threshold.',
    color: 'var(--error)'
  }
];
