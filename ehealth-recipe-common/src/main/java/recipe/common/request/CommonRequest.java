package recipe.common.request;

import java.util.Map;

/**
 * company: ngarihealth
 * author: 0184/yu_yun
 * date:2018/3/8
 */
public class CommonRequest {

    private Map<String, Object> conditions;

    public Map<String, Object> getConditions() {
        return conditions;
    }

    public void setConditions(Map<String, Object> conditions) {
        this.conditions = conditions;
    }
}
