package recipe.drugsenterprise.bean;

import ctd.schema.annotation.Schema;
import ctd.util.JSONUtils;

import java.io.Serializable;

/**
 * 药企标准接口返回
 * company: ngarihealth
 *
 * @author: 0184/yu_yun
 * date:2017/4/18.
 */
@Schema
public class StandardResult<T> implements Serializable {

    private static final long serialVersionUID = -1977068545638974594L;

    public final static String SUCCESS = "000";

    public final static String FAIL = "001";

    public final static String DUPLICATION = "002";

    private String code;

    private String msg;

    private T data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return JSONUtils.toString(this);
    }
}
