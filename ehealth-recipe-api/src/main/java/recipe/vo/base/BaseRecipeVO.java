package recipe.vo.base;

import ctd.schema.annotation.ItemProperty;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * 处方基础的字段定义
 *
 * @author yinsheng
 */
@Getter
@Setter
public class BaseRecipeVO implements Serializable {
    private static final long serialVersionUID = -3089232276968472055L;
    @ItemProperty(alias = "处方序号")
    private Integer recipeId;

    @ItemProperty(alias = "订单编号")
    private String orderCode;

    @ItemProperty(alias = "医生姓名")
    private String doctorName;

    @ItemProperty(alias = "患者姓名")
    private String patientName;

    @ItemProperty(alias = "患者医院ID")
    private String patientID;

    @ItemProperty(alias = "开方机构")
    private Integer clinicOrgan;

    @ItemProperty(alias = "开方机构名称")
    private String organName;

    @ItemProperty(alias = "审方机构名称")
    private String checkOrganName;

    @ItemProperty(alias = "医院线下处方号")
    private String recipeCode;

    @ItemProperty(alias = "处方类型 1 西药 2 中成药")
    private Integer recipeType;

    @ItemProperty(alias = "挂号科室编码")
    private String appointDepart;

    @ItemProperty(alias = "挂号科室名称")
    private String appointDepartName;

    @ItemProperty(alias = "开方时间")
    private Date createDate;

    @ItemProperty(alias = "中药贴数")
    private Integer copyNum;

    @ItemProperty(alias = "机构疾病名称")
    private String organDiseaseName;

    @ItemProperty(alias = "机构疾病编码")
    private String organDiseaseId;

    @ItemProperty(alias = "审核日期")
    private Date checkDate;

    @ItemProperty(alias = "医生签名的处方PDF")
    private String signFile;

    @ItemProperty(alias = "药师签名的处方PDF")
    private String chemistSignFile;

    @ItemProperty(alias = "诊断备注")
    private String memo;

    @ItemProperty(alias = "处方备注")
    private String recipeMemo;

    @ItemProperty(alias = "失效时间")
    private Date invalidTime;

    @ItemProperty(alias = "制法")
    private String makeMethodId;

    @ItemProperty(alias = "制法text")
    private String makeMethodText;

    @ItemProperty(alias = "每贴次数")
    private String everyTcmNumFre;

    @ItemProperty(alias = "每付取汁")
    private String juice;

    @ItemProperty(alias = "每付取汁单位")
    private String juiceUnit;

    @ItemProperty(alias = "次量")
    private String minor;

    @ItemProperty(alias = "次量单位")
    private String minorUnit;

    @ItemProperty(alias = "中医症候编码")
    private String symptomId;

    @ItemProperty(alias = "中医症候名称")
    private String symptomName;

    @ItemProperty(alias = "煎法")
    private String decoctionId;

    @ItemProperty(alias = "煎法text")
    private String decoctionText;

    @ItemProperty(alias = "煎法单价")
    private Double decoctionPrice;

    @ItemProperty(alias = "病历号")
    private String medicalRecordNumber;
}
