package recipe.caNew.pdf.service;

import com.ngari.base.esign.model.CoOrdinateVO;
import com.ngari.base.esign.model.SignRecipePdfVO;
import com.ngari.his.ca.model.CaSealRequestTO;
import com.ngari.recipe.dto.SignImgNode;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;

import java.math.BigDecimal;
import java.util.List;

/**
 * pdf 业务接口
 *
 * @author fuzi
 */
public interface CreatePdfService {

    /**
     * 获取 pdf byte 文件
     *
     * @param recipe
     * @return
     * @throws Exception
     */
    byte[] queryPdfByte(Recipe recipe) throws Exception;

    /**
     * 获取pdf oss id
     *
     * @param recipe 处方信息
     * @return
     */
    byte[] queryPdfOssId(Recipe recipe) throws Exception;

    /**
     * 获取pdf Byte字节 给前端SDK
     *
     * @param recipeId 处方信息
     * @param data     pdf 文件
     * @return
     */
    CaSealRequestTO queryPdfBase64(byte[] data, Integer recipeId) throws Exception;

    /**
     * 在pdf中添加 医生签名
     *
     * @param data        pdf 文件
     * @param recipeId    处方id
     * @param signImgNode 签名对象
     * @return
     * @throws Exception
     */
    String updateDoctorNamePdf(byte[] data, Integer recipeId, SignImgNode signImgNode) throws Exception;

    /**
     * 个性化处方笺配置的填充字段中新增“异常用药签名”，在存在超量，十八反，十九畏的情况下需要将医生的签名图片展示在异常用药签名字段上
     *
     * @param data
     * @param recipe
     * @return
     */
    byte[] tcmContraindicationTypePdf(byte[] data, Recipe recipe) throws Exception;

    /**
     * 获取药师签名 pdf Byte字节 给前端SDK
     *
     * @param recipe 处方信息
     * @return
     */
    CaSealRequestTO queryCheckPdfByte(Recipe recipe);

    /**
     * 在pdf中添加 药师签名
     *
     * @param recipe      处方信息
     * @param signImageId 药师签名
     * @return 文件 fileId
     * @throws Exception
     */
    String updateCheckNamePdf(Recipe recipe, String signImageId) throws Exception;

    /**
     * 在pdf中添加 药师签名 E签宝
     *
     * @param recipeId 处方id
     * @param pdfEsign E签宝签名对象
     * @return
     * @throws Exception
     */
    byte[] updateCheckNamePdfEsign(Integer recipeId, SignRecipePdfVO pdfEsign) throws Exception;

    /**
     * 在pdf中添加 药品金额
     *
     * @param recipe
     * @param recipeFee
     * @return
     */
    CoOrdinateVO updateTotalPdf(Recipe recipe, BigDecimal recipeFee);

    /**
     * 在pdf中添加 发药时间
     *
     * @param recipe
     * @param dispensingTime
     * @return
     */
    CoOrdinateVO updateDispensingTimePdf(Recipe recipe, String dispensingTime);

    /**
     * pdf 处方号和患者病历号
     *
     * @param recipeId
     */
    String updateCodePdf(Recipe recipeId) throws Exception;

    /**
     * pdf 监管流水号
     *
     * @param fileId              文件id
     * @param superviseRecipeCode 监管流水号
     * @return
     */
    String updateSuperviseRecipeCodeExecute(Recipe recipe, String fileId, String superviseRecipeCode) throws Exception;

    /**
     * pdf  支付成功后 修改添加收货人信息/煎法
     *
     * @param recipeId
     */
    List<CoOrdinateVO> updateAddressPdf(Recipe recipeId, RecipeOrder order, String address);

    /**
     * pdf 核对发药
     *
     * @param recipe 处方
     * @return
     */
    SignImgNode updateGiveUser(Recipe recipe);

    /**
     * pdf 机构印章
     *
     * @param recipeId    处方id
     * @param organSealId 印章文件id
     * @param fileId      pdf文件id
     * @return
     */
    SignImgNode updateSealPdf(Integer recipeId, String organSealId, String fileId);
}
