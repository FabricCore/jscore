package ws.siri.jscore.runtime;

public class Errors {
    public static class TypeMismatchException extends RuntimeException {
        private String expects, got;

        public TypeMismatchException(String expects, String got) {
            this.expects = expects;
            this.got = got;
        }

        @Override
        public String toString() {
            return String.format("expects %s, got %s", expects, got);
        }
    }
}
