package com.yuvraj.resumescreener.domain.ai

import com.yuvraj.resumescreener.domain.model.JobDescription
import com.yuvraj.resumescreener.domain.model.Resume
import kotlinx.serialization.json.Json

/**
 * Prompts carried over from the Python source.
 *
 * [SCORING_TEMPLATE] is a byte-faithful copy of the prompt in
 * `backend/nodes/match_score_node.py`, extracted programmatically rather than
 * retyped. The weights, the score bands and the no-inference instruction are
 * product behavior, so `ScoringPromptTest` fails the build if any of them drift.
 *
 * The only intentional edit is the required-output block, which gained the
 * `breakdown` object required by decision d7. Everything above it is unchanged.
 */
object Prompts {

    private val json = Json { encodeDefaults = true; explicitNulls = false }

    private const val RESUME_TOKEN = "__RESUME_JSON__"
    private const val JD_TOKEN = "__JD_JSON__"

    /** Frame a resume for extraction. Mirrors the source schema's field descriptions. */
    fun extractResume(resumeText: String): String = """
You are an expert technical recruiter. Extract structured information from the following resume text.

Return the candidate name, email address, GitHub link, LinkedIn link, phone number, skills,
education, experience and projects exactly as they appear. Do not invent or guess any
information. Use an empty string for a field that is not present in the resume.

Resume text:
$resumeText
""".trimIndent()

    /** Frame a job description for extraction. Mirrors the source schema's field descriptions. */
    fun extractJobDescription(jdText: String): String = """
You are an expert technical recruiter. Extract structured information from the following job description.

Return the job title, a comma-separated string of required skills, the years of experience
required, the minimum education required, and a two sentence summary of the key
responsibilities. Do not invent or guess any information. Use an empty string for a field
that is not present in the job description.

Job description text:
$jdText
""".trimIndent()

    /** Substitute both structured inputs into the rubric. */
    fun score(resume: Resume, job: JobDescription): String = score(
        resumeJson = json.encodeToString(Resume.serializer(), resume),
        jdJson = json.encodeToString(JobDescription.serializer(), job),
    )

    fun score(resumeJson: String, jdJson: String): String = SCORING_TEMPLATE
        .replace(RESUME_TOKEN, resumeJson)
        .replace(JD_TOKEN, jdJson)

    /**
     * Verbatim from `match_score_node.py`, with the output block extended for d7.
     *
     * The `__RESUME_JSON__` and `__JD_JSON__` tokens stand in for the Python
     * f-string fields and are replaced by [score].
     */
    private val SCORING_TEMPLATE: String = """
You are an AI Hiring Evaluation Assistant designed to assess how well a candidate's resume matches a job description.

Your task:
Compare the candidate's **Resume Data** with the **Job Description Data** and evaluate the overall fit between them.
Use the criteria below to assign a numerical score (1–10) and provide a concise justification.

---

### Evaluation Criteria

**1. Skill Match (60%)**
- Check if the candidate's technical and soft skills overlap with the required skills in the job description.
- Higher weight is given for directly relevant technical skills or tools.
- Consider synonymous or related technologies (e.g., "TensorFlow" ≈ "Deep Learning" ≈ "Neural Networks").
- Deduct points if key required skills are missing or outdated.

**2. Experience Match (30%)**
- Evaluate relevance of the candidate's past roles, responsibilities, and industries to the job description.
- Consider recency, seniority, and project scope.
- Deduct points if experience is in unrelated domains or too limited for the expected role.

**3. Education Match (10%)**
- Assess whether the candidate's education level and field align with the job requirements.
- Deduct points only if the education level is significantly below expectations or unrelated.

---

### Scoring Guidelines
- Provide a single numerical **score between 1 and 10** (no decimals).
- 1–3 = Poor fit (missing key requirements)
- 4–6 = Partial fit (some overlap, lacks key experience)
- 7–8 = Good fit (strong match with few minor gaps)
- 9–10 = Excellent fit (high alignment in all criteria)

---

### Important Instructions
- Only use information explicitly provided in the resume and job description.
- Do NOT infer, assume, or guess missing data.
- Keep tone objective and factual.
- Justification should be recruiter-friendly and 2–4 sentences long.

---

### Input Data
**Resume Data (JSON):**
__RESUME_JSON__

**Job Description Data (JSON):**
__JD_JSON__

---

### Expected Output (JSON format)
{
  "score": <integer 1–10>,
  "summary": "<2–4 sentence explanation summarizing why this score was assigned.>",
  "breakdown": {
    "skills": <integer 1–10>,
    "experience": <integer 1–10>,
    "education": <integer 1–10>
  }
}
""".trimIndent() + "\n"
}
