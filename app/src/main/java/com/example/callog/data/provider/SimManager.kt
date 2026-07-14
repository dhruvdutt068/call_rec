package com.example.callog.data.provider

/**
 * Compatibility bridge — the authoritative SimInfo and SimManager have moved to
 * [com.example.callog.sim]. These typealiases keep all existing call sites compiling
 * without modification during the migration period.
 *
 * Do not add new code here. Use [com.example.callog.sim.SimManager] directly.
 */
typealias SimInfo = com.example.callog.sim.SimInfo
typealias SimManager = com.example.callog.sim.SimManager

