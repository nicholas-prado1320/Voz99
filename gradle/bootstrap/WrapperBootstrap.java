import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * First-run helper: downloads the OFFICIAL Gradle 8.13 Wrapper JAR and validates
 * it against Gradle's published checksum. Subsequent calls use the official jar.
 * This file is NOT a modified Gradle Wrapper; it only provisions the official file.
 */
public final class WrapperBootstrap {
    private static final String URL = "https://services.gradle.org/distributions/gradle-8.13-wrapper.jar";
    private static final String SHA256 = "81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f";

    public static void main(String[] args) throws Exception {
        var project = Path.of("").toAbsolutePath().normalize();
        var wrapper = project.resolve("gradle/wrapper/gradle-wrapper.jar");
        if (!Files.isRegularFile(wrapper) || !checkHash(wrapper)) {
            System.out.println("Obtendo Gradle Wrapper 8.13 oficial (SHA-256 verificado)...");
            var client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();
            var request = HttpRequest.newBuilder(URI.create(URL)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new IOException("Download do Wrapper falhou: HTTP " + response.statusCode());
            }
            Files.createDirectories(wrapper.getParent());
            var tmp = Files.createTempFile(wrapper.getParent(), "gradle-wrapper-", ".jar");
            try {
                Files.write(tmp, response.body());
                if (!checkHash(tmp)) throw new SecurityException("Checksum do Wrapper oficial não confere. Download descartado.");
                Files.move(tmp, wrapper, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(tmp);
            }
        }
        String javaExec = Path.of(System.getProperty("java.home"), "bin", System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        List<String> cmd = new ArrayList<>(List.of(javaExec, "-classpath", wrapper.toString(), "org.gradle.wrapper.GradleWrapperMain"));
        cmd.addAll(List.of(args));
        int code = new ProcessBuilder(cmd).directory(project.toFile()).inheritIO().start().waitFor();
        System.exit(code);
    }

    private static boolean checkHash(Path file) throws Exception {
        byte[] data = Files.readAllBytes(file);
        byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
        return SHA256.equalsIgnoreCase(HexFormat.of().formatHex(hash));
    }
}
