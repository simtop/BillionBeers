package com.simtop.billionbeers.debug

/**
 * Release twin of the debug-build [enableStrictMode] (app/src/debug) - same signature, no policy.
 * BillionBeersApplication calls this uniformly across build types.
 *
 * StrictMode is a development tool: its penalties cost work on every disk and network access, and
 * the violations it reports are for a developer to fix, not for a user to pay for.
 */
fun enableStrictMode() = Unit
