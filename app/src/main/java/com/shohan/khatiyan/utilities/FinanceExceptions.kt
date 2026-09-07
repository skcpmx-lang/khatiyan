package com.shohan.khatiyan.utilities

/** User-facing validation failure; [messageBn] is safe to show in a snackbar (Phase 36). */
class FinanceValidationException(val messageBn: String) : Exception(messageBn)
