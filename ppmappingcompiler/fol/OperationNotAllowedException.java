package ppmappingcompiler.fol;

@SuppressWarnings("unused")
public class OperationNotAllowedException extends Exception {

    public OperationNotAllowedException() {
        super();
    }

    public OperationNotAllowedException(String message) {
        super(message);
    }

}