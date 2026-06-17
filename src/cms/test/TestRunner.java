package cms.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class TestRunner {

    public static final AtomicInteger PASS = new AtomicInteger(0);
    public static final AtomicInteger FAIL = new AtomicInteger(0);

    public static int findFreePort() {
        for (int port = 15000 + (int)(Math.random() * 1000); port < 17999; port++) {
            try (Socket s = new Socket()) {
                s.bind(new InetSocketAddress("127.0.0.1", port));
                return port;
            } catch (IOException ignored) {}
        }
        throw new RuntimeException("No free port found");
    }

    public static void main(String[] args) throws Exception {
        int mockPort = findFreePort();
        ProcessBuilder mockPb = new ProcessBuilder("java", "-cp", "out", "cms.test.MockApi");
        mockPb.environment().put("PORT", String.valueOf(mockPort));
        mockPb.redirectError(ProcessBuilder.Redirect.DISCARD);
        mockPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process mock = mockPb.start();

        int adminPort = findFreePort();
        while (adminPort == mockPort) adminPort = findFreePort();
        ProcessBuilder adminPb = new ProcessBuilder("java", "-cp", "out", "cms.Server");
        adminPb.environment().put("PORT", String.valueOf(adminPort));
        adminPb.environment().put("API_BASE_URL", "http://127.0.0.1:" + mockPort);
        adminPb.redirectError(ProcessBuilder.Redirect.DISCARD);
        adminPb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process admin = adminPb.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            admin.destroy();
            mock.destroy();
            try { admin.waitFor(); } catch (InterruptedException ignored) {}
            try { mock.waitFor(); } catch (InterruptedException ignored) {}
        }));

        Helpers.setApiBase("http://127.0.0.1:" + mockPort);
        Helpers.setAdminBase("http://127.0.0.1:" + adminPort);
        if (!Helpers.waitForHealth(Helpers.getApiBase(), 10_000)) {
            admin.destroy(); mock.destroy();
            System.err.println("Mock API did not become healthy");
            System.exit(2);
        }
        if (!Helpers.waitForHealth(Helpers.getAdminBase(), 10_000)) {
            admin.destroy(); mock.destroy();
            System.err.println("Admin did not become healthy");
            System.exit(2);
        }

        TestContext ctx = new TestContext();

        AuthAdminTest.run(ctx);

        run("BlogPosting", cms.test.BlogPostingAdminTest::run, ctx);
        run("Person", cms.test.PersonAdminTest::run, ctx);
        run("Organization", cms.test.OrganizationAdminTest::run, ctx);
        run("WebPage", cms.test.WebPageAdminTest::run, ctx);
        run("ImageObject", cms.test.ImageObjectAdminTest::run, ctx);
        run("VideoObject", cms.test.VideoObjectAdminTest::run, ctx);
        run("AudioObject", cms.test.AudioObjectAdminTest::run, ctx);
        run("CategoryCode", cms.test.CategoryCodeAdminTest::run, ctx);
        run("CategoryCodeSet", cms.test.CategoryCodeSetAdminTest::run, ctx);
        run("DefinedTerm", cms.test.DefinedTermAdminTest::run, ctx);
        run("DefinedTermSet", cms.test.DefinedTermSetAdminTest::run, ctx);
        run("Comment", cms.test.CommentAdminTest::run, ctx);
        run("WebSite", cms.test.WebSiteAdminTest::run, ctx);
        run("SiteNavigationElement", cms.test.SiteNavigationElementAdminTest::run, ctx);

        int total = PASS.get() + FAIL.get();
        System.out.println();
        System.out.println("# tests " + total);
        System.out.println("# pass " + PASS.get());
        System.out.println("# fail " + FAIL.get());
        admin.destroy();
        mock.destroy();
        admin.waitFor();
        mock.waitFor();
        System.exit(FAIL.get() > 0 ? 1 : 0);
    }

    private static void run(String entity, Consumer<TestContext> tests, TestContext ctx) {
        ctx.currentEntity = entity;
        Helpers.resetSeedCache();
        tests.accept(ctx);
    }

    public static void recordPass(String name) {
        System.out.println("ok - " + name);
        PASS.incrementAndGet();
    }

    public static void recordFail(String name, Throwable e) {
        System.out.println("not ok - " + name);
        System.out.println("  " + e.getMessage());
        for (StackTraceElement el : e.getStackTrace()) System.out.println("  at " + el);
        FAIL.incrementAndGet();
    }

    public static class TestContext {
        public String currentEntity;

        // Entity-scoped test: prefixes the name with the entity and resets the
        // per-entity seed cache before each scenario.
        public void test(String name, ThrowingRunnable fn) {
            String fullName = currentEntity + ": " + name;
            try {
                Helpers.resetSeedCache();
                fn.run();
                recordPass(fullName);
            } catch (Throwable e) {
                recordFail(fullName, e);
            }
        }

        // Standalone test: no entity prefix, no seed reset. Used by the shared
        // auth/CSRF conformance scenarios.
        public void scenario(String name, ThrowingRunnable fn) {
            try {
                fn.run();
                recordPass(name);
            } catch (Throwable e) {
                recordFail(name, e);
            }
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
