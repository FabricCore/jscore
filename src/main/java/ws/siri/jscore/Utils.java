package ws.siri.jscore;

import java.util.Optional;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <T, U> Optional<U> dangerouslyCastOptional(Optional<T> value) {
        return (Optional<U>) value;
    }
}
