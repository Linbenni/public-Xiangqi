package com.sojourners.chess.openbook;

import com.sojourners.chess.config.ConfigProvider;
import com.sojourners.chess.model.BookData;
import com.sojourners.chess.util.FenUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 开局库聚合管理（桌面/安卓共用）。
 * 配置经 {@link ConfigProvider} 获取；本地库经 {@link SqliteAccessProvider} 打开。
 */
public class OpenBookManager {

    private volatile static OpenBookManager instance;

    private OpenBook cloudOpenBook;
    private List<OpenBook> localOpenBooks;
    private volatile List<String> lastOpenBookList = new ArrayList<>();

    private OpenBookManager() {
        this.cloudOpenBook = new CloudOpenBook();
        this.localOpenBooks = new ArrayList<>();

        setLocalOpenBooks();
    }

    public synchronized void close() {
        for (OpenBook ob : localOpenBooks) {
            ob.close();
        }
    }

    public synchronized void setLocalOpenBooks() {
        close();
        localOpenBooks.clear();
        List<String> paths = ConfigProvider.get().getOpenBookList();
        lastOpenBookList = new ArrayList<>(paths);
        for (String path : paths) {
            try {
                if (path.endsWith(".xqb")) {
                    localOpenBooks.add(new XqbOpenBook(path));
                } else if (path.endsWith(".obk")) {
                    localOpenBooks.add(new BhOpenBook(path));
                } else if (path.endsWith(".pfBook")) {
                    localOpenBooks.add(new PfOpenBook(path));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 配置变化后刷新本地库列表（避免重复重建）。
     */
    public synchronized void reloadIfChanged() {
        List<String> paths = ConfigProvider.get().getOpenBookList();
        if (!paths.equals(lastOpenBookList)) {
            setLocalOpenBooks();
        }
    }

    public synchronized List<BookData> queryBook(char[][] b, boolean redGo, boolean offManual) {

        List<BookData> cloudResults = new ArrayList<>();
        if (ConfigProvider.get().getUseCloudBook()) {
            String fenCode = FenUtils.fenCode(b, redGo);
            cloudResults.addAll(cloudOpenBook.query(fenCode, offManual, ConfigProvider.get().getMoveRule()));
        }

        List<BookData> localResults = new ArrayList<>();
        if (!offManual) {
            for (OpenBook ob : this.localOpenBooks) {
                localResults.addAll(ob.query(b, redGo, ConfigProvider.get().getMoveRule()));
            }
        }

        if (ConfigProvider.get().getLocalBookFirst()) {
            localResults.addAll(cloudResults);
            return localResults;
        } else {
            cloudResults.addAll(localResults);
            return cloudResults;
        }
    }

    public static OpenBookManager getInstance() {
        if (instance == null) {
            synchronized (OpenBookManager.class) {
                if (instance == null) {
                    instance = new OpenBookManager();
                }
            }
        }
        return instance;
    }

}
