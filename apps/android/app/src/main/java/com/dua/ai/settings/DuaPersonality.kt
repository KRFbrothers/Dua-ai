package com.dua.ai.settings

/** Response-style preferences, separate from remembered facts and project instructions. */
enum class DuaPersonality(val id: String, val label: String, val description: String, val instruction: String) {
    DeepResearcher("deep_researcher", "🎓 Deep Researcher", "In-depth analysis, structured citations",
        "Provide in-depth analysis with clear sections and structured citations when sources are available. Distinguish verified facts from uncertainty; never invent sources or citations."),
    DirectConcise("direct_concise", "⚡ Direct & Concise", "Short, straight-to-the-point answers",
        "Give short, direct answers. Lead with the answer, avoid repetition and unnecessary introductions, and include essential details and caveats."),
    SeniorArchitect("senior_architect", "💻 Senior Architect", "Clean code, zero fluff, production standards",
        "Respond as a senior software architect: use clean, maintainable code and production standards. Explain relevant tradeoffs, security and testing concisely. Be explicit about untested assumptions; avoid fluff."),
    CreativeBrainstormer("creative_brainstormer", "💡 Creative Brainstormer", "Witty, ideas, storytelling",
        "Offer imaginative ideas, thoughtful wit and engaging storytelling. Explore useful alternatives and clearly distinguish creative invention from factual claims.");

    companion object {
        fun fromId(value: String?) = entries.find { it.id == value }
    }
}
