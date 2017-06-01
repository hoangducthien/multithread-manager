package tek912.com.multithreadmanager;

import android.content.Context;
import android.os.Handler;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Hỗ trợ việc xử lý các thao tác có thời gian xử lý dài (thường là trên 200ms)
 * như load dữ liệu từ server về, nguyên nhân thường do việc tải các dữ liệu từ
 * server về hoặc parse các dữ liệu từ cache mất khá nhiều thời gian, nếu thực
 * hiện trong main thread sẽ làm block ui, ngược lại nếu các màn hình khác nhau
 * đều tự tạo và quản lý thread một cách độc lập thì sẽ khó kiểm soát số lượng
 * thread của toàn bộ ứng dụng, có thể sẽ gây ảnh hưởng cả về memory, network
 * resource...
 * <p>
 * Với AsyncTaskManager này, mọi request gửi tới đều được đưa vào thread pool để
 * xử lý (xem {@link #mTaskThreadPool}f), số lượng thread sẽ được giới hạn ở mức
 * độ phù hợp, bên cạnh đó có một thread với độ ưu tiên cao dành có các request
 * yêu cầu được thực thi gấp, nếu không thì UI sẽ phải chờ
 *
 * @author Tran Vu Tat Binh (tranvutatbinh@gmail.com)
 */
public class AsyncTaskManager {
    /**
     * Độ ưu tiên bình thường, dành cho các task mà UI không bị block khi chờ
     * kết quả
     */
    public static final int PRIORITY_NORMAL = 0;

    public static final int PRIORITY_HIGHER = 1;

    public static final int PRIORITY_RUN_ON_MAIN = 2;

    public static final int PRIORITY_RUN_ON_MAIN_FRONT = 3;

    private static final Object mLock = new Object();

    // Singleton
    private static AsyncTaskManager mInstance;

    //Khi sử dụng class này mà không có context thì bật cờ này lên để init lại class này
    // trong trường hợp sau  có truyền vào context
    private static boolean mNeedReInit;

    /**
     * Số lượng thread trong thread pool
     */
    private static final int CORE_NORMAL_POOL_SIZE = 4;

    /**
     * Số lượng thread tối đa trong thread pool, hiện tại để bằng số lượng trong
     * trường hợp bình thường để giới hạn tại mức đó luôn
     */
    private static final int MAXIMUM_NORMAL_POOL_SIZE = 4;

    /**
     * Thời gian giữ một thread tồn tại để chờ dùng lại sau khi thực thi xong
     */
    private static final int KEEP_ALIVE_TIME = 2;

    /**
     * Thread pool để xử lý các task thông thường không đòi hỏi độ ưu tiên cao
     */
    private ThreadPoolExecutor mTaskThreadPool;

    /**
     * Handler để gửi callback kết quả lên UI thread
     */
    private Handler mUICallbackHandler;

    /*
     * Thread pool dành cho urgent task, thông thường chỉ cần dùng normal task,
     * urgent task chỉ dùng đến khi thực sự cần ưu tiên cao vì số thread này
     * giới hạn và dành riêng cho những task đặc thù như khi mở một màn hình
     * đang hiện loading dialog thì nên dùng cái này và khi loading dialog đóng
     * thì cái này đã xong, những việc ko block màn hình để chờ thì dùng normal
     * thread pool là được
     */
    private static final int CORE_URGENT_POOL_SIZE = 2;
    private static final int MAXIMUM_URGENT_POOL_SIZE = 2;

    //Khi sử dụng thread pool này làm UI bị đứng, cần check lại
    private ThreadPoolExecutor mUrgentTaskThreadPool;

    public static AsyncTaskManager getInstance(Context context) {
        synchronized (mLock) {
            if (mInstance == null || (mNeedReInit && context != null)) {
                mInstance = new AsyncTaskManager(
                        context.getApplicationContext());
            }
            return mInstance;
        }
    }

    public static AsyncTaskManager getInstance() {
        synchronized (mLock) {
            if (mInstance == null) {
                mInstance = new AsyncTaskManager(null);
            }
            return mInstance;
        }
    }

    private AsyncTaskManager(Context context) {

        // Những task background có độ ưu tiên trung bình
        /*
      Hàng đợi các task cần thực thi với thread pool
     */
        if (!mNeedReInit) {
            BlockingQueue<Runnable> normalTaskQueue = new LinkedBlockingQueue<Runnable>();
            mTaskThreadPool = new ThreadPoolExecutor(CORE_NORMAL_POOL_SIZE,
                    MAXIMUM_NORMAL_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    normalTaskQueue);
            mTaskThreadPool.allowCoreThreadTimeOut(true);

            // Những task background có độ ưu tiên cao
            BlockingQueue<Runnable> urgentTaskQueue = new LinkedBlockingQueue<Runnable>();
            mUrgentTaskThreadPool = new ThreadPoolExecutor(CORE_URGENT_POOL_SIZE,
                    MAXIMUM_URGENT_POOL_SIZE, KEEP_ALIVE_TIME, TimeUnit.SECONDS,
                    urgentTaskQueue);
            mUrgentTaskThreadPool.allowCoreThreadTimeOut(true);
            mUrgentTaskThreadPool.setThreadFactory(new ThreadFactory() {

                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r);
                    t.setPriority(Thread.NORM_PRIORITY + 1);
                    return t;
                }
            });
        }

        // UI worker handler
        if (context != null) {
            mUICallbackHandler = new Handler(context.getMainLooper());
            mNeedReInit = false;
        } else {
            mNeedReInit = true;
        }
    }

    public <T extends Object> void successCallbackOnUIThread(
            final TAsyncCallback<T> resultCallback, final T result) {
        if (resultCallback == null) {
            return;
        }

        if (mUICallbackHandler == null) {
            throw new NullPointerException("Need context object to call this method");
        }

        mUICallbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onSuccess(result);
            }
        });
    }

    public <T extends Object> void errorCallbackOnUIThread(
            final TAsyncCallback<T> resultCallback, final TError error) {
        if (resultCallback == null) {
            return;
        }

        if (mUICallbackHandler == null) {
            throw new NullPointerException("Need context object to call this method");
        }

        mUICallbackHandler.post(new Runnable() {
            @Override
            public void run() {
                resultCallback.onError(error);
            }
        });
    }

    /**
     * Gửi yêu cầu thực thi một công việc nào đó
     */
    public void execute(Runnable runnable) {
        execute(runnable, PRIORITY_NORMAL);
    }

    /**
     * Đăng ký thực thi một tác vụ load dữ liệu nào đó, truyền vào đối tượng
     * DataLoaderTask mô tả tác vụ và độ ưu tiên để sắp xếp lại thứ tự thực thi
     * nếu cần thiết, trong trường hợp rất gấp có thể ko dùng đến phương thức
     * này của DataLoader vì không chắc chắn 100% là thực thi được ngay lập tức <br>
     * <br>
     * <p>
     * <b>Lưu ý:</b> hiện tại độ ưu tiên chưa được implement, model này vẫn đang
     * trong quá trình xây dựng
     */
    public void execute(Runnable runnable, int priority) {
        if (priority == PRIORITY_RUN_ON_MAIN_FRONT) {
            mUICallbackHandler.postAtFrontOfQueue(runnable);
        } else if (priority == PRIORITY_RUN_ON_MAIN) {
            mUICallbackHandler.post(runnable);
        } else if (priority == PRIORITY_HIGHER) {
            mUrgentTaskThreadPool.execute(runnable);
        } else {
            mTaskThreadPool.execute(runnable);
        }
    }

    /**
     * Hoãn thực thi một task nào đó
     */
    public void cancel(Runnable runnable) {
        mTaskThreadPool.remove(runnable);
        mUrgentTaskThreadPool.remove(runnable);
    }

}
