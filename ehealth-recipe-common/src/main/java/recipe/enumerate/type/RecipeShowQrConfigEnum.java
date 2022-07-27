package recipe.enumerate.type;

/**
 * opbase配置 处方展示取药二维码配置类型
 * 运营平台配置项(退费限制 getQrTypeForRecipe)
 * @author yinsheng
 * @date 2021\8\23 0023 10:18
 */
public enum  RecipeShowQrConfigEnum {

    NO_HAVE(1, "无"),
    CARD_NO(2, "就诊卡号"),
    REGISTER_ID(3, "挂号序号"),
    PATIENT_ID(4, "患者ID"),
    RECIPE_CODE(5, "his处方单号"),
    SERIALNUMBER(6, "医院药房发药编号"),
    TAKE_DRUG_CODE(7,"药柜药品取件码"),
    MEDICAL_RECORD_NUMBER(8,"病历号"),
    CERTIFICATE(9, "身份证号"),
    SERIALNUMBER_TAKE_DRUG_CODE(10, "药房+药柜取件码");

    private Integer type;
    private String name;

    RecipeShowQrConfigEnum(Integer type, String name){
        this.type = type;
        this.name = name;
    }

    public static RecipeShowQrConfigEnum getEnumByType(Integer type){
        RecipeShowQrConfigEnum[] enums = RecipeShowQrConfigEnum.values();
        for (RecipeShowQrConfigEnum configEnum : enums) {
            if (configEnum.getType().equals(type)) {
                return configEnum;
            }
        }
        return NO_HAVE;
    }

    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }
}
