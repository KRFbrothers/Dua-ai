package com.dua.ai.model

/** A request through the existing provider sandbox; only returned CodeRun records prove execution. */
object CodeSnippet {
    fun prompt(code: String): String =
        "Run the following Python code in your code execution sandbox. Report actual output or errors; do not invent output if execution is unavailable.\n" +
            "````python\n" + code + "\n````"
}
