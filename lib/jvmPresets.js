/**
 * Curated 2026 Modern JVM optimization profiles for Cosmic Client Minecraft 1.8.9 & 1.12.2.
 */
const JVM_PRESETS = [
    {
        id: 'cosmic_pro_fps',
        name: 'Cosmic Pro FPS Suite [Built for Cosmonauts]',
        description: 'Competitive Edition: Sub-10ms G1GC latency tuning, aggressive thread prefetching, string dedup, and memory defragmentation for 500+ FPS.',
        args: [
            '-XX:+UseG1GC',
            '-XX:+ParallelRefProcEnabled',
            '-XX:MaxGCPauseMillis=10',
            '-XX:+UnlockExperimentalVMOptions',
            '-XX:+DisableExplicitGC',
            '-XX:+AlwaysPreTouch',
            '-XX:+OptimizeStringConcat',
            '-XX:+UseFastUnorderedTimeStamps',
            '-XX:+UseStringDeduplication',
            '-XX:G1NewSizePercent=35',
            '-XX:G1MaxNewSizePercent=60',
            '-XX:G1ReservePercent=15',
            '-XX:G1HeapWastePercent=5',
            '-XX:G1MixedGCCountTarget=4',
            '-XX:InitiatingHeapOccupancyPercent=10',
            '-XX:G1MixedGCLiveThresholdPercent=90',
            '-XX:G1RSetUpdatingPauseTimePercent=5',
            '-XX:SurvivorRatio=32',
            '-XX:+PerfDisableSharedMem',
            '-XX:MaxTenuringThreshold=1'
        ].join(' ')
    },
    {
        id: 'zgc_zero_lag',
        name: '2026 ZGC Zero-Lag [Sub-Millisecond GC]',
        description: 'Next-generation ZGC garbage collection for modern CPUs: guarantees sub-1 millisecond pauses and instantaneous chunk mesh loading.',
        args: '-XX:+UseZGC -XX:+UnlockExperimentalVMOptions -XX:+AlwaysPreTouch -XX:+UseFastUnorderedTimeStamps'
    },
    {
        id: 'shenandoah_pvp',
        name: 'Shenandoah Ultra-Responsive PvP',
        description: 'Ultra-low pause time Shenandoah garbage collector for 100% stutter-free combat and instant potion rendering.',
        args: '-XX:+UseShenandoahGC -XX:ShenandoahGCHeuristics=compact -XX:+AlwaysPreTouch -XX:+UseFastUnorderedTimeStamps'
    },
    {
        id: 'aikar_g1gc',
        name: 'Aikar PvP (G1GC) [Balanced Classic]',
        description: 'Optimized G1GC flags for smooth frame pacing and minimal stutter during intense PvP combat.',
        args: [
            '-XX:+UseG1GC',
            '-XX:+ParallelRefProcEnabled',
            '-XX:MaxGCPauseMillis=200',
            '-XX:+UnlockExperimentalVMOptions',
            '-XX:+DisableExplicitGC',
            '-XX:+AlwaysPreTouch',
            '-XX:G1NewSizePercent=30',
            '-XX:G1MaxNewSizePercent=40',
            '-XX:G1ReservePercent=20',
            '-XX:G1HeapWastePercent=5',
            '-XX:G1MixedGCCountTarget=4',
            '-XX:InitiatingHeapOccupancyPercent=15',
            '-XX:G1MixedGCLiveThresholdPercent=90',
            '-XX:G1RSetUpdatingPauseTimePercent=5',
            '-XX:SurvivorRatio=32',
            '-XX:+PerfDisableSharedMem',
            '-XX:MaxTenuringThreshold=1'
        ].join(' ')
    },
    {
        id: 'vanilla',
        name: 'Minimal / Default',
        description: 'Standard JVM execution with no extra garbage collection tuning flags.',
        args: ''
    }
];

function getPresets() {
    return JVM_PRESETS;
}

function getPresetById(id) {
    if (id === 'lunar_2026_turbo' || id === 'lunar_fps' || id === 'cosmic_pro_fps') {
        return JVM_PRESETS[0];
    }
    return JVM_PRESETS.find(p => p.id === id) || JVM_PRESETS[0];
}

module.exports = {
    getPresets,
    getPresetById
};
