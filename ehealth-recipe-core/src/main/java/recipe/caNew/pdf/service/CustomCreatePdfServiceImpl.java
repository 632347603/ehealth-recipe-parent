package recipe.caNew.pdf.service;

import com.alibaba.fastjson.JSON;
import com.itextpdf.text.Image;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.*;
import com.ngari.base.esign.model.CoOrdinateVO;
import com.ngari.base.esign.model.SignRecipePdfVO;
import com.ngari.base.esign.service.IESignBaseService;
import com.ngari.his.ca.model.CaSealRequestTO;
import com.ngari.recipe.dto.RecipeInfoDTO;
import com.ngari.recipe.dto.RecipeLabelVO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.entity.RecipeOrder;
import com.ngari.recipe.entity.Recipedetail;
import ctd.persistence.exception.DAOException;
import lombok.Cleanup;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import recipe.bussutil.BarCodeUtil;
import recipe.bussutil.CreateRecipePdfUtil;
import recipe.bussutil.SignImgNode;
import recipe.bussutil.WordToPdfBean;
import recipe.caNew.pdf.CreatePdfFactory;
import recipe.client.IConfigurationClient;
import recipe.constant.ErrorCode;
import recipe.constant.OperationConstant;
import recipe.dao.RecipeExtendDAO;
import recipe.manager.RecipeManager;
import recipe.manager.RedisManager;
import recipe.util.ByteUtils;
import recipe.util.DictionaryUtil;
import recipe.util.MapValueUtil;
import recipe.util.RecipeUtil;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.util.*;

import static recipe.constant.OperationConstant.*;
import static recipe.util.ByteUtils.DOT_EN;

/**
 * 自定义创建pdf
 * 根据自定义工具画模版 方式生成的业务处理代码类
 *
 * @author fuzi
 */
@Service
public class CustomCreatePdfServiceImpl implements CreatePdfService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());


    /**
     * 需要记录坐标的的字段
     */
    private final String RECIPE = OP_RECIPE + DOT_EN;
    private final List<String> ADDITIONAL_FIELDS = Arrays.asList(RECIPE + OP_RECIPE_DOCTOR, RECIPE + OP_RECIPE_CHECKER,
            RECIPE + OP_RECIPE_GIVE_USER, RECIPE + OP_RECIPE_ACTUAL_PRICE, OP_BARCODE_ALL, "recipe.patientID", "recipe.recipeCode"
            , "address", "recipeExtend.decoctionText", "recipeExtend.superviseRecipecode", "recipe.organName");
    @Autowired
    private RedisManager redisManager;
    @Autowired
    private IConfigurationClient configurationClient;
    @Autowired
    private RecipeManager recipeManager;
    @Autowired
    private RecipeExtendDAO recipeExtendDAO;
    @Resource
    private IESignBaseService esignService;


    @Override
    public byte[] queryPdfByte(Recipe recipe) throws Exception {
        logger.info("CustomCreatePdfServiceImpl queryPdfByte recipe = {}", recipe.getRecipeId());
        return generateTemplatePdf(recipe);
    }

    @Override
    public byte[] queryPdfOssId(Recipe recipe) throws Exception {
        byte[] data = generateTemplatePdf(recipe);
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipe.getRecipeId(), RECIPE + OP_RECIPE_DOCTOR);
        if (null == ordinateVO) {
            return null;
        }
        SignRecipePdfVO pdfEsign = new SignRecipePdfVO();
        pdfEsign.setQrCodeSign(true);
        pdfEsign.setPosX(ordinateVO.getX().floatValue());
        pdfEsign.setPosY((float) ordinateVO.getY() - 25);
        pdfEsign.setWidth(150f);
        pdfEsign.setData(data);
        pdfEsign.setFileName("recipe_" + recipe.getRecipeId() + ".pdf");
        pdfEsign.setDoctorId(recipe.getDoctor());
        data = esignService.signForRecipe2(pdfEsign);
        logger.info("CustomCreatePdfServiceImpl queryPdfOssId data:{}", data.length);
        return data;
    }

    @Override
    public CaSealRequestTO queryPdfBase64(byte[] data, Integer recipeId) throws Exception {
        logger.info("CustomCreatePdfServiceImpl queryPdfByte recipe = {}", recipeId);
        String pdfBase64Str = new String(Base64.encode(data));
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipeId, RECIPE + OP_RECIPE_DOCTOR);
        if (null == ordinateVO) {
            return null;
        }
        return CreatePdfFactory.caSealRequestTO(ordinateVO.getX(), ordinateVO.getY(), recipeId.toString(), pdfBase64Str);
    }


    @Override
    public String updateDoctorNamePdf(byte[] data, Integer recipeId, SignImgNode signImgNode) throws Exception {
        logger.info("CustomCreatePdfServiceImpl updateDoctorNamePdf signImgNode = {}", JSON.toJSONString(signImgNode));
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipeId, RECIPE + OP_RECIPE_DOCTOR);
        if (null == ordinateVO) {
            return null;
        }
        signImgNode.setSignFileData(data);
        signImgNode.setX(ordinateVO.getX().floatValue());
        signImgNode.setY(ordinateVO.getY().floatValue());
        return CreateRecipePdfUtil.generateSignImgNode(signImgNode);
    }

    @Override
    public CaSealRequestTO queryCheckPdfByte(Recipe recipe) {
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipe.getRecipeId(), RECIPE + OP_RECIPE_CHECKER);
        if (null == ordinateVO) {
            return null;
        }
        return CreatePdfFactory.caSealRequestTO(ordinateVO.getX(), ordinateVO.getY(), "check" + recipe.getRecipeId(), CreateRecipePdfUtil.signFileBase64(recipe.getSignFile()));
    }

    @Override
    public String updateCheckNamePdf(Recipe recipe, String signImageId) throws Exception {
        String recipeId = recipe.getRecipeId().toString();
        logger.info("CustomCreatePdfServiceImpl updateCheckNamePdf recipeId:{}", recipeId);
        //更新pdf文件
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipe.getRecipeId(), RECIPE + OP_RECIPE_CHECKER);
        if (null == ordinateVO) {
            return null;
        }
        if (StringUtils.isNotEmpty(signImageId)) {
            SignImgNode signImgNode = new SignImgNode(recipeId, signImageId, recipe.getSignFile(), null,
                    40f, 20f, ordinateVO.getX().floatValue(), ordinateVO.getY().floatValue(), false);
            return CreateRecipePdfUtil.generateSignImgNode(signImgNode);
        } else if (StringUtils.isNotEmpty(recipe.getCheckerText())) {
            CoOrdinateVO coords = new CoOrdinateVO();
            coords.setValue(recipe.getCheckerText());
            coords.setX(ordinateVO.getX());
            coords.setY(ordinateVO.getY());
            return CreateRecipePdfUtil.generateCoOrdinatePdf(recipe.getSignFile(), coords);
        }
        return null;
    }

    @Override
    public byte[] updateCheckNamePdfEsign(Integer recipeId, SignRecipePdfVO pdfEsign) throws Exception {
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipeId, RECIPE + OP_RECIPE_CHECKER);
        if (null == ordinateVO) {
            return null;
        }
        pdfEsign.setPosX((float) ordinateVO.getX() + 20);
        pdfEsign.setPosY((float) ordinateVO.getY());
        byte[] data = esignService.signForRecipe2(pdfEsign);
        logger.info("CustomCreatePdfServiceImpl updateCheckNamePdfEsign data:{}", data.length);
        return data;
    }

    @Override
    public CoOrdinateVO updateTotalPdf(Recipe recipe, BigDecimal recipeFee) {
        logger.info("CustomCreatePdfServiceImpl updateTotalPdf  recipeId={},recipeFee={}", recipe.getRecipeId(), recipeFee);
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipe.getRecipeId(), RECIPE + OP_RECIPE_ACTUAL_PRICE);
        if (null == ordinateVO) {
            return null;
        }
        CoOrdinateVO coords = new CoOrdinateVO();
        coords.setValue(recipeFee.toString());
        coords.setX(ordinateVO.getX());
        coords.setY(ordinateVO.getY());
        coords.setRepeatWrite(true);
        return coords;
    }


    @Override
    public String updateCodePdf(Recipe recipe) throws Exception {
        Integer recipeId = recipe.getRecipeId();
        logger.info("CustomCreatePdfServiceImpl updateCodePdf  recipeId={}", recipeId);
        CoOrdinateVO barcode = redisManager.getPdfCoords(recipe.getRecipeId(), OP_BARCODE_ALL);
        if (null != barcode) {
            String barCode = configurationClient.getValueCatch(recipe.getClinicOrgan(), OperationConstant.OP_BARCODE, "");
            String[] keySplit = barCode.trim().split(ByteUtils.DOT);
            if (2 == keySplit.length) {
                String fieldName = keySplit[1];
                barcode.setValue(MapValueUtil.getFieldValueByName(fieldName, recipe));
            }
        }
        List<CoOrdinateVO> coOrdinateList = new LinkedList<>();
        CoOrdinateVO patientId = redisManager.getPdfCoords(recipe.getRecipeId(), "recipe.patientID");
        if (null != patientId) {
            patientId.setValue(recipe.getPatientID());
            coOrdinateList.add(patientId);
        }
        CoOrdinateVO recipeCode = redisManager.getPdfCoords(recipeId, "recipe.recipeCode");
        if (null != recipeCode) {
            recipeCode.setValue(recipe.getRecipeCode());
            coOrdinateList.add(recipeCode);
        }
        return CreateRecipePdfUtil.generateRecipeCodeAndPatientIdForRecipePdf(recipe.getSignFile(), coOrdinateList, barcode);
    }


    @Override
    public List<CoOrdinateVO> updateAddressPdf(Recipe recipe, RecipeOrder order, String address) {
        logger.info("CustomCreatePdfServiceImpl updateAddressPdfExecute  recipeId={}", recipe.getRecipeId());
        List<CoOrdinateVO> list = new LinkedList<>();
        //患者端煎法生效
        String decoctionDeploy = configurationClient.getValueEnumCatch(recipe.getClinicOrgan(), "decoctionDeploy", null);
        if (null != decoctionDeploy) {
            CoOrdinateVO decoctionText = redisManager.getPdfCoords(recipe.getRecipeId(), "recipeExtend.decoctionText");
            RecipeExtend recipeExtend = recipeExtendDAO.getByRecipeId(recipe.getRecipeId());
            if (null != decoctionText && null != recipeExtend && StringUtils.isEmpty(recipeExtend.getDecoctionText())) {
                decoctionText.setValue(recipeExtend.getDecoctionText());
                list.add(decoctionText);
            }
        }
        CoOrdinateVO addressOrdinate = redisManager.getPdfCoords(recipe.getRecipeId(), "address");
        if (null != addressOrdinate) {
            addressOrdinate.setValue(address);
            CoOrdinateVO receiver = new CoOrdinateVO();
            receiver.setX(addressOrdinate.getX());
            receiver.setY(addressOrdinate.getY() - 12);
            receiver.setValue("收货人姓名: " + order.getReceiver() + " " + "收货人电话: " + order.getRecMobile());
            list.add(receiver);
            list.add(addressOrdinate);
        }
        logger.info("CustomCreatePdfServiceImpl updateAddressPdf   list ={}", JSON.toJSONString(list));
        return list;
    }

    @Override
    public SignImgNode updateGiveUser(Recipe recipe) {
        logger.info("CustomCreatePdfServiceImpl updateGiveUser recipe={}", JSON.toJSONString(recipe));
        //修改pdf文件
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipe.getRecipeId(), RECIPE + OP_RECIPE_GIVE_USER);
        if (null == ordinateVO) {
            return null;
        }
        return new SignImgNode(recipe.getRecipeId().toString(), null, null, null,
                50f, 20f, ordinateVO.getX().floatValue(), ordinateVO.getY().floatValue(), true);
    }

    @Override
    public SignImgNode updateSealPdf(Integer recipeId, String organSealId, String fileId) {
        CoOrdinateVO ordinateVO = redisManager.getPdfCoords(recipeId, "recipe.organName");
        if (null == ordinateVO) {
            return null;
        }
        return new SignImgNode(recipeId.toString(), organSealId, fileId, null, 90F, 90F
                , ordinateVO.getX().floatValue(), ordinateVO.getY().floatValue(), false);
    }

    /**
     * 按照模版生成 pdf
     *
     * @param recipe 处方
     * @return pdf byte
     */
    private byte[] generateTemplatePdf(Recipe recipe) throws Exception {
        //模版pdfId
        String organSealId;
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            organSealId = configurationClient.getValueCatch(recipe.getClinicOrgan(), OperationConstant.OP_CONFIG_PDF_CHINA, "");
        } else {
            organSealId = configurationClient.getValueCatch(recipe.getClinicOrgan(), OperationConstant.OP_CONFIG_PDF, "");
        }
        //下载模版
        @Cleanup InputStream input = new ByteArrayInputStream(CreateRecipePdfUtil.signFileByte(organSealId));
        @Cleanup ByteArrayOutputStream output = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(input);
        PdfStamper stamper = new PdfStamper(reader, output);
        AcroFields form = stamper.getAcroFields();
        form.addSubstitutionFont(BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED));
        //记录坐标的的字段对象
        redisManager.coOrdinate(recipe.getRecipeId(), ordinateList(form));
        //需要替换的模版字段对象
        List<WordToPdfBean> generatePdfList = templatePdfList(recipe, form);
        //如果为false，生成的PDF文件可以编辑，如果为true，生成的PDF文件不可以编辑
        stamper.setFormFlattening(true);
        stamper.close();
        //拷贝模版流 生成新pdf
        byte[] data = CreateRecipePdfUtil.generateTemplatePdf(recipe.getRecipeId(), output);
        //删除生成的图片
        try {
            generatePdfList.stream().filter(a -> null != a.getUri()).forEach(a -> new File(a.getUri()).delete());
        } catch (Exception e) {
            logger.error("CustomCreatePdfServiceImpl generateTemplatePdf  error File ={}", JSON.toJSONString(generatePdfList), e);
        }
        logger.info("CustomCreatePdfServiceImpl generateTemplatePdf data={}", data.length);
        return data;
    }

    /**
     * 需要替换的模版字段对象
     *
     * @param recipe
     * @param form
     * @return
     */
    private List<WordToPdfBean> templatePdfList(Recipe recipe, AcroFields form) {
        Map<String, AcroFields.Item> map = form.getFields();
        if (map.isEmpty()) {
            logger.warn("CustomCreatePdfServiceImpl generatePdfList map null");
            throw new DAOException(ErrorCode.SERVICE_ERROR, "模版错误");
        }
        //获取pdf值对象
        RecipeInfoDTO recipePdfDTO = recipeManager.getRecipeInfoDTO(recipe.getRecipeId());
        //获取模版填充字段
        List<WordToPdfBean> generatePdfList = generatePdfList(recipe.getClinicOrgan(), map.keySet(), recipePdfDTO);
        //替换的模版字段
        for (WordToPdfBean wordToPdf : generatePdfList) {
            try {
                String key = wordToPdf.getKey();
                if (null == wordToPdf.getUri()) {
                    //文字类的内容处理
                    form.setField(key, wordToPdf.getValue());
                } else {
                    //将图片写入指定的field
                    Image image = Image.getInstance(wordToPdf.getUri().toURL());
                    PushbuttonField pb = form.getNewPushbuttonFromField(key);
                    pb.setImage(image);
                    form.replacePushbuttonField(key, pb.getField());
                }
            } catch (Exception e) {
                logger.error("CreateRecipePdfUtil templatePdfList error ", e);
            }
        }
        return generatePdfList;
    }

    /**
     * 获取模版填充字段,根据模版表单字段解析 反射获取值
     *
     * @param keySet       模版表单字段
     * @param recipePdfDTO pdf值对象
     * @return 模版填充对象
     */
    private List<WordToPdfBean> generatePdfList(Integer organId, Set<String> keySet, RecipeInfoDTO recipePdfDTO) {
        logger.warn("CustomCreatePdfServiceImpl generatePdfList organId:{}, keySet : {}", organId, JSON.toJSONString(keySet));
        List<WordToPdfBean> generatePdfList = new LinkedList<>();
        Map<String, Object> recipeDetailMap;
        if (RecipeUtil.isTcmType(recipePdfDTO.getRecipe().getRecipeType())) {
            //中药
            recipeDetailMap = createChineMedicinePDF(recipePdfDTO);
        } else {
            //西药
            recipeDetailMap = createMedicinePDF(recipePdfDTO);
        }
        for (String key : keySet) {
            String[] keySplit = key.trim().split(ByteUtils.DOT);
            //对象字段处理
            if (2 == keySplit.length) {
                String objectName = keySplit[0];
                String fieldName = keySplit[1];
                generatePdfList.add(invokeFieldName(key, objectName, fieldName, recipePdfDTO, recipeDetailMap));
                continue;
            }
            //特殊节点处理
            if (3 == keySplit.length) {
                String identifyName = keySplit[0];
                String objectName = keySplit[1];
                String fieldName = keySplit[2];
                //条形码
                if (OperationConstant.OP_BARCODE.equals(identifyName)) {
                    String barCode = configurationClient.getValueCatch(organId, OperationConstant.OP_BARCODE, "");
                    if (StringUtils.isNotEmpty(barCode)) {
                        String[] barCodes = barCode.trim().split(ByteUtils.DOT);
                        objectName = barCodes[0];
                        fieldName = barCodes[1];
                    }
                    WordToPdfBean wordToPdf = invokeFieldName(key, objectName, fieldName, recipePdfDTO, null);
                    URI uri = BarCodeUtil.generateFileUrl(wordToPdf.getValue(), "barcode.png");
                    wordToPdf.setUri(uri);
                    generatePdfList.add(wordToPdf);
                }
                //二维码
                if (OperationConstant.OP_QRCODE.equals(identifyName)) {
                    generatePdfList.add(invokeFieldName(key, objectName, fieldName, recipePdfDTO, null));
                }
            }
        }
        logger.warn("CustomCreatePdfServiceImpl generatePdfList generatePdfList : {}", JSON.toJSONString(generatePdfList));
        return generatePdfList;
    }

    /**
     * 反射获取所需字段值
     *
     * @param key          表单名
     * @param objectName   对象名
     * @param fieldName    字段名
     * @param recipePdfDTO pdf值对象
     * @return 对应的value
     */
    private WordToPdfBean invokeFieldName(String key, String objectName, String fieldName, RecipeInfoDTO recipePdfDTO, Map<String, Object> recipeDetailMap) {
        if (OP_RECIPE.equals(objectName)) {
            String value = MapValueUtil.getFieldValueByName(fieldName, recipePdfDTO.getRecipe());
            if (OP_RECIPE_DOCTOR.equals(fieldName)) {
                value = "";
            }
            if (OperationConstant.OP_RECIPE_RECIPE_MEMO.equals(fieldName)) {
                value = configurationClient.getValueCatch(recipePdfDTO.getRecipe().getClinicOrgan(), "recipeDetailRemark", "");
            }
            return new WordToPdfBean(key, value, null);
        }
        if (OP_PATIENT.equals(objectName)) {
            String value = MapValueUtil.getFieldValueByName(fieldName, recipePdfDTO.getPatientBean());
            if ("patientUserType".equals(fieldName)) {
                value = StringUtils.isEmpty(value) || "0".equals(value) ? "普通" : "儿科";
            }
            return new WordToPdfBean(key, value, null);
        }
        if (OP_RECIPE_EXTEND.equals(objectName)) {
            String value = MapValueUtil.getFieldValueByName(fieldName, recipePdfDTO.getRecipeExtend());
            return new WordToPdfBean(key, value, null);
        }
        if (OP_RECIPE_DETAIL.equals(objectName)) {
            String value = ByteUtils.objValueOfString(recipeDetailMap.get(key));
            return new WordToPdfBean(key, value, null);
        }
        return new WordToPdfBean();
    }

    /**
     * 中药
     *
     * @param recipeInfoDTO 处方明细
     * @return
     */
    private Map<String, Object> createChineMedicinePDF(RecipeInfoDTO recipeInfoDTO) {
        List<Recipedetail> recipeDetails = recipeInfoDTO.getRecipeDetails();
        if (CollectionUtils.isEmpty(recipeDetails)) {
            return null;
        }
        List<RecipeLabelVO> list = new LinkedList<>();
        for (int i = 0; i < recipeDetails.size(); i++) {
            String drugShowName = RecipeUtil.drugChineShowName(recipeDetails.get(i));
            list.add(new RecipeLabelVO("药品名称", "recipeDetail.drugName_" + i, drugShowName));
        }
        Recipedetail recipedetail = recipeDetails.get(0);
        list.add(new RecipeLabelVO("药房", "recipeDetail.pharmacyName", recipedetail.getPharmacyName()));
        list.add(new RecipeLabelVO("天数", "recipeDetail.useDays", CreatePdfFactory.getUseDays(recipedetail.getUseDaysB(), recipedetail.getUseDays())));
        list.add(new RecipeLabelVO("用药途径", "recipeDetail.usePathways", DictionaryUtil.getDictionary("eh.cdr.dictionary.UsePathways", recipedetail.getUsePathways())));
        list.add(new RecipeLabelVO("用药频次", "recipeDetail.usingRate", DictionaryUtil.getDictionary("eh.cdr.dictionary.UsingRate", recipedetail.getUsingRate())));
        Recipe recipe = recipeInfoDTO.getRecipe();
        list.add(new RecipeLabelVO("贴数", "recipe.copyNum", recipe.getCopyNum() + "贴"));
        list.add(new RecipeLabelVO("嘱托", "tcmRecipeMemo", ByteUtils.objValueOfString(recipe.getRecipeMemo())));
        RecipeExtend recipeExtend = recipeInfoDTO.getRecipeExtend();
        list.add(new RecipeLabelVO("煎法", "recipeExtend.decoctionText", ByteUtils.objValueOfString(recipeExtend.getDecoctionText())));
        list.add(new RecipeLabelVO("制法", "recipeExtend.makeMethodText", ByteUtils.objValueOfString(recipeExtend.getMakeMethodText())));
        list.add(new RecipeLabelVO("每付取汁", "recipeExtend.juice", ByteUtils.objValueOfString(recipeExtend.getJuice()) + ByteUtils.objValueOfString(recipeExtend.getJuiceUnit())));
        list.add(new RecipeLabelVO("每次用汁", "recipeExtend.minor", ByteUtils.objValueOfString(recipeExtend.getMinor()) + ByteUtils.objValueOfString(recipeExtend.getMinorUnit())));
        logger.info("CreateRecipePdfUtil createChineMedicinePDF list :{} ", JSON.toJSONString(list));
        return list.stream().collect(HashMap::new, (m, v) -> m.put(v.getEnglishName(), v.getValue()), HashMap::putAll);
    }


    /**
     * 西药
     *
     * @param recipeInfoDTO 处方明细
     * @return
     */
    private Map<String, Object> createMedicinePDF(RecipeInfoDTO recipeInfoDTO) {
        List<Recipedetail> recipeDetails = recipeInfoDTO.getRecipeDetails();
        if (CollectionUtils.isEmpty(recipeDetails)) {
            return null;
        }
        List<RecipeLabelVO> list = new LinkedList<>();
        for (int i = 0; i < recipeDetails.size(); i++) {
            Recipedetail detail = recipeDetails.get(i);
            list.add(new RecipeLabelVO("药品名称", "recipeDetail.drugName_" + i, detail.getDrugName()));
            list.add(new RecipeLabelVO("包装规格", "recipeDetail.drugSpec_" + i, ByteUtils.objValueOfString(detail.getDrugSpec()) + "/" + ByteUtils.objValueOfString(detail.getDrugUnit())));
            list.add(new RecipeLabelVO("发药数量", "recipeDetail.useTotalDose_" + i, ByteUtils.objValueOfString(detail.getUseTotalDose()) + ByteUtils.objValueOfString(detail.getDrugUnit())));
            list.add(new RecipeLabelVO("每次用量", "recipeDetail.useDose_" + i, "Sig: 每次 " + ByteUtils.objValueOfString(detail.getUseDose()) + ByteUtils.objValueOfString(detail.getUseDoseUnit())));
            list.add(new RecipeLabelVO("用药频次", "recipeDetail.usingRate_" + i, DictionaryUtil.getDictionary("eh.cdr.dictionary.UsingRate", detail.getUsingRate())));
            list.add(new RecipeLabelVO("用药途径", "recipeDetail.usePathways_" + i, DictionaryUtil.getDictionary("eh.cdr.dictionary.UsePathways", detail.getUsePathways())));
            list.add(new RecipeLabelVO("用药天数", "recipeDetail.useDays_" + i, CreatePdfFactory.getUseDays(detail.getUseDaysB(), detail.getUseDays())));
            list.add(new RecipeLabelVO("用药天数", "recipeDetail.memo_" + i, detail.getMemo()));
        }
        Recipedetail recipedetail = recipeDetails.get(0);
        list.add(new RecipeLabelVO("药房", "recipeDetail.pharmacyName", recipedetail.getPharmacyName()));
        logger.info("CreateRecipePdfUtil createMedicinePDF list :{} ", JSON.toJSONString(list));
        return list.stream().collect(HashMap::new, (m, v) -> m.put(v.getEnglishName(), v.getValue()), HashMap::putAll);
    }


    /**
     * 记录坐标的的字段对象
     *
     * @param form 模版表单
     * @return 需要记录坐标的的字段对象
     */
    private List<CoOrdinateVO> ordinateList(AcroFields form) {
        List<CoOrdinateVO> ordinateList = new LinkedList<>();
        ADDITIONAL_FIELDS.forEach(a -> {
            //定位某个表单字段坐标
            List<AcroFields.FieldPosition> pos = form.getFieldPositions(a);
            if (CollectionUtils.isEmpty(pos)) {
                return;
            }
            Rectangle pRectangle = pos.get(0).position;
            CoOrdinateVO coOrdinateVO = new CoOrdinateVO();
            coOrdinateVO.setX((int) pRectangle.getLeft());
            coOrdinateVO.setY((int) pRectangle.getBottom());
            coOrdinateVO.setName(a);
            ordinateList.add(coOrdinateVO);
        });
        return ordinateList;
    }

}
