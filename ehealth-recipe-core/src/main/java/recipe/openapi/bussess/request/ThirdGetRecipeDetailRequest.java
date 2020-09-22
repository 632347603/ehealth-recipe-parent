package recipe.openapi.bussess.request;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;

/**
 * @author yinsheng
 * @date 2020\9\18 0018 16:00
 */
@Data
@EqualsAndHashCode(callSuper=true)
public class ThirdGetRecipeDetailRequest extends ThirdBaseRequest implements Serializable{
    private static final long serialVersionUID = -6496187637005413012L;

    private String tabStatus;

    private Integer index;

    private Integer limit;
}
