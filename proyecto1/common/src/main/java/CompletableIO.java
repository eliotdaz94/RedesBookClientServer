import java.nio.channels.CompletionHandler;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * Clase completableIO encargada de proveer una estructura generica para los manejadores utilizados por las llamadas
 * read y write de los canales asincronos, de esta manera toda la informacion se maneja al momento de dispararse la
 * comunicacion.
 */
public class CompletableIO {

    public static <T, C> CompletableFuture<T> execute(Consumer<CompletionHandler<T, C>> f) {
        final CompletableFuture<T> futureResult = new CompletableFuture<>();
        final CompletionHandler<T,C> handler = new CompletionHandler<T, C>() {
            @Override
            public void completed(T result, C attachment) {
                futureResult.complete(result);
            }

            @Override
            public void failed(Throwable exc, C attachment) {
                futureResult.completeExceptionally(exc);
            }
        };

        f.accept(handler);
        return futureResult;
    }
}
