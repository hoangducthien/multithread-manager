package tek912.com.multithreadmanager;

/**
 * Created by hoangthien on 4/29/17.
 */

public class TError {

    public static final int NO_INTERNET = 101;

    private int mErrorCode;
    private int mStringId;
    private String mErrorMessage;

    public TError(int errorCode, String errorMessage) {
        mErrorCode = errorCode;
        mErrorMessage = errorMessage;
    }

    public TError(int errorCode, int errorMessageId) {
        mErrorCode = errorCode;
        mStringId = errorMessageId;
    }

    public TError(String errorMessage) {
        mErrorMessage = errorMessage;
    }

    public TError(int stringId) {
        mStringId = stringId;
    }

    public static TError noInternet() {
        return new TError(NO_INTERNET, R.string.no_internet);

    }

    public int getErrorCode() {
        return mErrorCode;
    }

    public void setErrorCode(int errorCode) {
        mErrorCode = errorCode;
    }

    public String getErrorMessage() {
        return mErrorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        mErrorMessage = errorMessage;
    }

    public int getStringId() {
        return mStringId;
    }

    public void setStringId(int stringId) {
        mStringId = stringId;
    }
}
