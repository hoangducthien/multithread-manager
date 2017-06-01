package tek912.com.multithreadmanager;

/**
 * Created by hoangthien on 5/2/17.
 */

public interface TAsyncCallback<E> {

    void onSuccess(E responseData);

    void onError(TError error);
}
