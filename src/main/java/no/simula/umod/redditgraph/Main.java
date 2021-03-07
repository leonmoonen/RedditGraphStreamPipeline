package no.simula.umod.redditgraph;

import akka.actor.ActorSystem;
import org.apache.commons.lang3.NotImplementedException;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.util.concurrent.Callable;

import static no.simula.umod.redditgraph.ConsoleUtils.logDuration;

enum ProgramMode {
    UnweightedGraph
}

@SuppressWarnings("unused")
@Command(name = "rgraph", mixinStandardHelpOptions = true, version = "not versioned / latest build from master",
        description = "RedditGraph for graph generation and experiments.")
class Main implements Callable<Integer> {

    private final ActorSystem actorSystem = ActorSystem.create("Graph");

    @Parameters(index = "0", description = "Valid values: ${COMPLETION-CANDIDATES}")
    private ProgramMode mode;

    @Parameters(index = "1", description = "File to lad the graph from.")
    private File file;

    @Option(names= {"--out-edge-csv"}, description = "Output file for the edge list csv.")
    private File outEdgeCsv;

    @Option(names= {"--out-vertex-csv"}, description = "Output file for the vertex list csv.")
    private File outVertexCsv;

    @Option(names= {"--out-dot"}, description = "Output file for the graph dot.")
    private File outDot;

    @Override
    public Integer call() throws Exception {
        if(mode == ProgramMode.UnweightedGraph){
            final var subredditGraph = new SubRedditGraph(actorSystem);

            // Import and create
            subredditGraph.createCountListFromCsv(file);

            // Parallel export
            final var startTime = System.nanoTime();

            var dotFuture = subredditGraph.exportDot(outDot).thenRunAsync(() ->
                    logDuration("Exported dot", startTime)
            ).toCompletableFuture();

            var edgeCsvFuture = subredditGraph.exportEdgeList(outEdgeCsv).thenRunAsync(() ->
                    logDuration("Exported edge list", startTime)
            ).toCompletableFuture();

            var vertexCsvFuture = subredditGraph.exportVertexList(outVertexCsv).thenRunAsync(() ->
                    logDuration("Exported vertex list", startTime)
            ).toCompletableFuture();

            dotFuture.join();
            edgeCsvFuture.join();
            vertexCsvFuture.join();
        }
        else {
            throw new NotImplementedException("Mode not implemented");
        }

        return 0;
    }

    /**
     * Parses the command line arguments an runs the callable
     * @param args command line arguments
     */
    public static void main(String... args) {
        long startTime = System.nanoTime();
        int exitCode = new CommandLine(new Main()).execute(args);
        logDuration("Finished program run", startTime);
        System.exit(exitCode);
    }
}