package recipe.enumerate.type;

import org.apache.commons.lang3.StringUtils;

/**
 * 处方剂型类型 1 中药饮片 2 配方颗粒
 */
public enum RecipeDrugFormTypeEnum {

    TCM_DECOCTION_PIECES(1, "饮片方", "中药饮片"),
    TCM_FORMULA_PIECES(2, "颗粒方", "配方颗粒");

    private Integer type;
    private String name;
    private String desc;

    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    RecipeDrugFormTypeEnum(Integer type, String name, String desc){
        this.type = type;
        this.name = name;
        this.desc = desc;
    }

    public static String getDrugForm(Integer type) {
        for (RecipeDrugFormTypeEnum e : RecipeDrugFormTypeEnum.values()) {
            if (e.type.equals(type)) {
                return e.desc;
            }
        }
        return "";
    }

    public static Integer getDrugFormType(String desc){
        if (StringUtils.isEmpty(desc)) {
            return TCM_DECOCTION_PIECES.type;
        }
        for (RecipeDrugFormTypeEnum e : RecipeDrugFormTypeEnum.values()) {
            if (e.desc.equals(desc.replace("\n", "").replace("\r", ""))) {
                return e.type;
            }
        }
        return null;
    }
}
