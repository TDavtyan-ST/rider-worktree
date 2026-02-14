package com.github.tdavtyan.riderworktrees.services

import com.github.tdavtyan.riderworktrees.model.FileChange
import com.github.tdavtyan.riderworktrees.model.WorktreeInfo
import com.github.tdavtyan.riderworktrees.settings.WorktreeSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.config.GitExecutableManager
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

class WorktreeServiceImpl(private val project: Project) : WorktreeService {

    private val LOG = Logger.getInstance(WorktreeServiceImpl::class.java)

    private data class CacheEntry(val timestamp: Long, val worktrees: List<WorktreeInfo>)

    private val cache = AtomicReference<CacheEntry?>(null)

    private fun getRepository(): GitRepository? {
        val repos = GitRepositoryManager.getInstance(project).repositories
        LOG.info("getRepository() for project '${project.name}': found ${repos.size} repos: ${repos.map { it.root.path }}")
        return repos.firstOrNull()
    }

    private fun getGitExecutable(): String {
        return GitExecutableManager.getInstance().getExecutable(project).exePath
    }

    private fun runGitWorktreeCommand(repo: GitRepository, vararg args: String): ProcessResult {
        val commandLine = GeneralCommandLine()
        commandLine.exePath = getGitExecutable()
        commandLine.setWorkDirectory(repo.root.path)
        commandLine.addParameters("worktree")
        commandLine.addParameters(*args)
        commandLine.charset = Charsets.UTF_8

        val handler = CapturingProcessHandler(commandLine)
        val output = handler.runProcess(30_000)

        return ProcessResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
        )
    }

    private data class ProcessResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String,
    ) {
        val success: Boolean get() = exitCode == 0
        val outputLines: List<String> get() = stdout.lines()
    }

    override fun listWorktrees(): List<WorktreeInfo> {
        val cached = cache.get()
        if (cached != null && System.currentTimeMillis() - cached.timestamp < CACHE_TTL_MS) {
            LOG.info("listWorktrees(): returning ${cached.worktrees.size} cached worktrees")
            return cached.worktrees
        }

        val repo = getRepository()
        if (repo == null) {
            LOG.warn("listWorktrees(): no git repository found for project '${project.name}' (basePath=${project.basePath})")
            return emptyList()
        }

        LOG.info("listWorktrees(): running 'git worktree list --porcelain' in ${repo.root.path}")
        val result = runGitWorktreeCommand(repo, "list", "--porcelain")
        if (!result.success) {
            LOG.error("listWorktrees(): git command failed with exit code ${result.exitCode}, stderr: ${result.stderr}")
            return emptyList()
        }

        LOG.info("listWorktrees(): raw output:\n${result.stdout}")
        val worktrees = parsePorcelainOutput(result.outputLines, repo.root.path)
        LOG.info("listWorktrees(): parsed ${worktrees.size} worktrees")
        cache.set(CacheEntry(System.currentTimeMillis(), worktrees))
        return worktrees
    }

    override fun getCachedWorktrees(): List<WorktreeInfo>? {
        return cache.get()?.worktrees
    }

    private fun parsePorcelainOutput(lines: List<String>, currentRoot: String): List<WorktreeInfo> {
        val worktrees = mutableListOf<WorktreeInfo>()
        var path: String? = null
        var commitHash = ""
        var branch: String? = null
        var isLocked = false
        var isPrunable = false
        var isFirst = true

        for (line in lines) {
            when {
                line.startsWith("worktree ") -> {
                    path = line.removePrefix("worktree ").trim()
                }
                line.startsWith("HEAD ") -> {
                    commitHash = line.removePrefix("HEAD ").trim()
                }
                line.startsWith("branch ") -> {
                    branch = line.removePrefix("branch ").trim()
                }
                line == "detached" -> {
                    branch = null
                }
                line == "locked" -> {
                    isLocked = true
                }
                line == "prunable" -> {
                    isPrunable = true
                }
                line.isBlank() -> {
                    if (path != null) {
                        worktrees.add(
                            WorktreeInfo(
                                path = Paths.get(path),
                                branch = branch,
                                commitHash = commitHash,
                                isMain = isFirst,
                                isCurrent = normalizedPathEquals(path, currentRoot),
                                isLocked = isLocked,
                                isPrunable = isPrunable,
                            )
                        )
                    }
                    path = null
                    commitHash = ""
                    branch = null
                    isLocked = false
                    isPrunable = false
                    isFirst = false
                }
            }
        }

        // Handle last entry if output doesn't end with blank line
        if (path != null) {
            worktrees.add(
                WorktreeInfo(
                    path = Paths.get(path),
                    branch = branch,
                    commitHash = commitHash,
                    isMain = isFirst,
                    isCurrent = normalizedPathEquals(path, currentRoot),
                    isLocked = isLocked,
                    isPrunable = isPrunable,
                )
            )
        }

        return worktrees
    }

    private fun normalizedPathEquals(a: String, b: String): Boolean {
        return try {
            // Use toRealPath() to resolve symlinks (e.g., /tmp -> /private/tmp on macOS)
            val pathA = Paths.get(a).toRealPath()
            val pathB = Paths.get(b).toRealPath()
            val result = pathA == pathB
            LOG.info("normalizedPathEquals: '$a' -> '$pathA' vs '$b' -> '$pathB' = $result")
            result
        } catch (_: Exception) {
            // Fallback: try without resolving symlinks
            try {
                Paths.get(a).toAbsolutePath().normalize() == Paths.get(b).toAbsolutePath().normalize()
            } catch (_: Exception) {
                a == b
            }
        }
    }

    private fun runGitCommandInDir(workDir: String, vararg args: String): ProcessResult {
        val commandLine = GeneralCommandLine()
        commandLine.exePath = getGitExecutable()
        commandLine.setWorkDirectory(workDir)
        commandLine.addParameters(*args)
        commandLine.charset = Charsets.UTF_8

        val handler = CapturingProcessHandler(commandLine)
        val output = handler.runProcess(30_000)

        return ProcessResult(
            exitCode = output.exitCode,
            stdout = output.stdout,
            stderr = output.stderr,
        )
    }

    override fun getWorktreeStatus(worktree: WorktreeInfo): List<FileChange> {
        val result = runGitCommandInDir(worktree.path.toString(), "status", "--porcelain")
        if (!result.success) {
            LOG.warn("getWorktreeStatus failed for ${worktree.path}: ${result.stderr}")
            return emptyList()
        }

        return result.stdout.lines()
            .filter { it.length >= 3 }
            .map { line ->
                val statusCode = line.substring(0, 2).trim()
                val filePath = line.substring(3).trim()
                FileChange(
                    relativePath = filePath,
                    status = FileChange.Status.fromGitCode(statusCode),
                    worktreePath = worktree.path,
                )
            }
    }

    override fun getFileDiff(fileChange: FileChange): String {
        val args = if (fileChange.status == FileChange.Status.UNTRACKED) {
            arrayOf("diff", "--no-index", "/dev/null", fileChange.relativePath)
        } else {
            arrayOf("diff", "HEAD", "--", fileChange.relativePath)
        }

        val result = runGitCommandInDir(fileChange.worktreePath.toString(), *args)
        // diff returns exit code 1 when there are differences, which is normal
        return result.stdout.ifBlank { result.stderr.ifBlank { "No diff available" } }
    }

    override fun createWorktree(branch: String, path: Path, createNewBranch: Boolean): Result<Unit> {
        val repo = getRepository() ?: return Result.failure(IllegalStateException("No git repository found"))

        val args = mutableListOf("add")
        if (createNewBranch) {
            args.addAll(listOf("-b", branch, path.toString()))
        } else {
            args.addAll(listOf(path.toString(), branch))
        }

        val result = runGitWorktreeCommand(repo, *args.toTypedArray())
        invalidateCache()
        return if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException(result.stderr.ifBlank { "Unknown error" }))
        }
    }

    override fun removeWorktree(info: WorktreeInfo, force: Boolean): Result<Unit> {
        val repo = getRepository() ?: return Result.failure(IllegalStateException("No git repository found"))

        val args = mutableListOf("remove")
        if (force) {
            args.add("--force")
        }
        args.add(info.path.toString())

        val result = runGitWorktreeCommand(repo, *args.toTypedArray())
        invalidateCache()
        return if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException(result.stderr.ifBlank { "Unknown error" }))
        }
    }

    override fun lockWorktree(info: WorktreeInfo, reason: String?): Result<Unit> {
        val repo = getRepository() ?: return Result.failure(IllegalStateException("No git repository found"))

        val args = mutableListOf("lock")
        if (!reason.isNullOrBlank()) {
            args.addAll(listOf("--reason", reason))
        }
        args.add(info.path.toString())

        val result = runGitWorktreeCommand(repo, *args.toTypedArray())
        invalidateCache()
        return if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException(result.stderr.ifBlank { "Unknown error" }))
        }
    }

    override fun unlockWorktree(info: WorktreeInfo): Result<Unit> {
        val repo = getRepository() ?: return Result.failure(IllegalStateException("No git repository found"))

        val result = runGitWorktreeCommand(repo, "unlock", info.path.toString())
        invalidateCache()
        return if (result.success) {
            Result.success(Unit)
        } else {
            Result.failure(RuntimeException(result.stderr.ifBlank { "Unknown error" }))
        }
    }

    override fun pruneWorktrees(): Result<String> {
        val repo = getRepository() ?: return Result.failure(IllegalStateException("No git repository found"))

        val result = runGitWorktreeCommand(repo, "prune", "--verbose")
        invalidateCache()
        return if (result.success) {
            val output = result.stdout.trim().ifBlank { "No stale worktrees found." }
            Result.success(output)
        } else {
            Result.failure(RuntimeException(result.stderr.ifBlank { "Unknown error" }))
        }
    }

    override fun getDefaultWorktreePath(branchName: String): Path? {
        val repo = getRepository() ?: return null
        val repoRoot = Paths.get(repo.root.path)
        val settings = WorktreeSettings.getInstance(project)
        val sanitized = branchName
            .replace("/", "-")
            .replace("\\", "-")
            .replace(" ", "-")
        return repoRoot.resolve(settings.state.defaultWorktreeDirectory).resolve(sanitized)
    }

    override fun getAvailableBranches(): List<String> {
        val repo = getRepository() ?: return emptyList()
        val local = repo.branches.localBranches.map { it.name }.sorted()
        val remote = repo.branches.remoteBranches.map { it.name }.sorted()
        return local + remote
    }

    override fun invalidateCache() {
        cache.set(null)
    }

    companion object {
        private const val CACHE_TTL_MS = 5000L
    }
}
