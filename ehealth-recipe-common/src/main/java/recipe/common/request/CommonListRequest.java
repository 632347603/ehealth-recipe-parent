package recipe.common.request;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2018/3/8
 */
public class CommonListRequest extends CommonRequest{

    protected int start;

    protected int limit;

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
