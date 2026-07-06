package com.devpulse.shared.github;

import com.devpulse.shared.github.GitHubTree;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

@Component
public class GitHubClient {

    private final RestClient restClient;

    public GitHubClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public List<GitHubCommitResponse> fetchCommitsForDate(
            String owner, String repo, String username, LocalDate date) {

        String since = date.atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        String until = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toString();

        GitHubCommitResponse[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?author={username}&since={since}&until={until}",
                        owner, repo, username, since, until)
                .retrieve()
                .body(GitHubCommitResponse[].class);

        return response != null ? List.of(response) : List.of();
    }

    public List<GitHubCommitResponse> fetchRecentCommits(String owner, String repo, String username, int limit) {
        GitHubCommitResponse[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/commits?author={username}&per_page={limit}",
                        owner, repo, username, limit)
                .retrieve()
                .body(GitHubCommitResponse[].class);

        return response != null ? List.of(response) : List.of();
    }
    public List<GitHubPullRequestFile> fetchPullRequestFiles(String owner, String repo, int prNumber) {
        GitHubPullRequestFile[] response = restClient.get()
                .uri("/repos/{owner}/{repo}/pulls/{number}/files", owner, repo, prNumber)
                .retrieve()
                .body(GitHubPullRequestFile[].class);

        return response != null ? List.of(response) : List.of();
     }
    public GitHubCommentResponse postIssueComment(String owner, String repo, int issueNumber, String commentBody) {
        return restClient.post()
                .uri("/repos/{owner}/{repo}/issues/{number}/comments", owner, repo, issueNumber)
                .body(Map.of("body", commentBody))
                .retrieve()
                .body(GitHubCommentResponse.class);
    }
    public GitHubTree fetchRepositoryTree(String owner, String repo, String branch) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/git/trees/{branch}?recursive=1",owner, repo, branch)
                .retrieve()
                .body(GitHubTree.class);
    }

    public GitHubFileContent fetchFileContent(String owner, String repo, String path) {
        return restClient.get()
                .uri("/repos/{owner}/{repo}/contents/{path}", owner, repo, path)
                .retrieve()
                .body(GitHubFileContent.class);
    }

     public List<GitHubFileContent> fetchRepositoryFiles(String owner, String repo, String branch) {
        GitHubTree tree = fetchRepositoryTree(owner, repo, branch);
        if (tree == null || tree.tree() == null) return List.of();

                return tree.tree().stream()
                .filter(item -> "blob".equals(item.type()))
                .filter(item -> isIndexableFile(item.path()))
                .limit(200)
                .map(item -> {
                try {
                        return fetchFileContent(owner, repo, item.path());
                } catch (Exception e) {
                        return null;
                }
            })
            .filter(f -> f != null)
            .collect(java.util.stream.Collectors.toList());
       }

        private boolean isIndexableFile(String path) {
        String lower = path.toLowerCase();
                return (lower.endsWith(".java") || lower.endsWith(".kt") ||
                lower.endsWith(".md") || lower.endsWith(".yml") ||
                lower.endsWith(".yaml") || lower.endsWith(".properties"))
                && !lower.contains("/target/")
                && !lower.contains("/build/")
                && !lower.contains("/node_modules/");
        }
}