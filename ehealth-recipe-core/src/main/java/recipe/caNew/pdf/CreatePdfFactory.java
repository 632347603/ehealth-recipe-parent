package recipe.caNew.pdf;

import com.alibaba.fastjson.JSON;
import com.ngari.base.esign.model.CoOrdinateVO;
import com.ngari.base.esign.model.SignRecipePdfVO;
import com.ngari.his.ca.model.CaSealRequestTO;
import com.ngari.patient.dto.DoctorExtendDTO;
import com.ngari.recipe.dto.ApothecaryDTO;
import com.ngari.recipe.dto.AttachSealPicDTO;
import com.ngari.recipe.entity.Recipe;
import com.ngari.recipe.entity.RecipeOrder;
import ctd.persistence.exception.DAOException;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import recipe.bussutil.CreateRecipePdfUtil;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.SignImgNode;
import recipe.caNew.pdf.service.CreatePdfService;
import recipe.client.DoctorClient;
import recipe.client.IConfigurationClient;
import recipe.constant.ErrorCode;
import recipe.constant.OperationConstant;
import recipe.dao.RecipeDAO;
import recipe.dao.RecipeOrderDAO;
import recipe.enumerate.type.SignImageTypeEnum;
import recipe.manager.SignManager;
import recipe.service.RecipeLogService;
import recipe.thread.RecipeBusiThreadPool;
import recipe.util.ByteUtils;
import recipe.util.ValidateUtil;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.List;

import static recipe.util.DictionaryUtil.getDictionary;

/**
 * pdf 构建工厂类
 *
 * @author fuzi
 */
@Service
public class CreatePdfFactory {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Resource(name = "platformCreatePdfServiceImpl")
    private CreatePdfService platformCreatePdfServiceImpl;
    @Resource(name = "customCreatePdfServiceImpl")
    private CreatePdfService customCreatePdfServiceImpl;
    @Autowired
    private IConfigurationClient configurationClient;
    @Autowired
    private RecipeDAO recipeDAO;
    @Autowired
    private RecipeOrderDAO orderDAO;
    @Autowired
    private SignManager signManager;
    @Autowired
    protected DoctorClient doctorClient;

    /**
     * 获取pdf oss id
     *
     * @param recipe
     * @return
     */
    public void queryPdfOssId(Recipe recipe) {
        logger.info("CreatePdfFactory queryPdfOssId recipe:{}", recipe.getRecipeId());
        CreatePdfService createPdfService = createPdfService(recipe);
        try {
            byte[] data = createPdfService.queryPdfOssId(recipe);
            if (null == data) {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "获取pdf_oss_id格式生成null");
                return;
            }
            String fileId = CreateRecipePdfUtil.signFileByte(data, "recipe_" + recipe.getRecipeId() + ".pdf");
            Recipe recipeUpdate = new Recipe();
            recipeUpdate.setRecipeId(recipe.getRecipeId());
            recipeUpdate.setSignFile(fileId);
            recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
            logger.info("CreatePdfFactory queryPdfOssId recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
        } catch (Exception e) {
            logger.error("CreatePdfFactory queryPdfOssId 使用平台医生部分pdf的,生成失败 recipe:{}", recipe.getRecipeId(), e);
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "获取pdf_oss_id格式生成失败");
        }
    }

    /**
     * 获取pdf byte 格式
     *
     * @param recipeId
     * @return
     */
    public CaSealRequestTO queryPdfByte(Integer recipeId) {
        logger.info("CreatePdfFactory queryPdfByte recipe:{}", recipeId);
        Recipe recipe = validate(recipeId);
        CreatePdfService createPdfService = createPdfService(recipe);
        try {
            byte[] data = createPdfService.queryPdfByte(recipe);
            CaSealRequestTO caSealRequest = createPdfService.queryPdfBase64(data, recipe.getRecipeId());
            if (null == caSealRequest) {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "获取pdf_byte格式生成null");
            }
            return caSealRequest;
        } catch (Exception e) {
            logger.error("CreatePdfFactory queryPdfByte 使用平台医生部分pdf的,生成失败 recipe:{}", recipe.getRecipeId(), e);
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "获取pdf_byte格式生成失败");
            return null;
        }
    }

    /**
     * 医生签名 标准对接CA方式 并返回CA对象
     *
     * @param recipe
     * @return
     * @throws Exception
     */
    public CaSealRequestTO updateDoctorNamePdfV1(Recipe recipe) throws Exception {
        logger.info("CreatePdfFactory updateDoctorNamePdfV1 recipe:{}", recipe.getRecipeId());
        CreatePdfService createPdfService = createPdfService(recipe);
        byte[] data = createPdfService.queryPdfByte(recipe);
        updateDoctorNamePdf(recipe, data, createPdfService);
        CaSealRequestTO requestSealTO = createPdfService.queryPdfBase64(data, recipe.getRecipeId());
        requestSealTO.setSealBase64Str("");
        //获取签章图片
        DoctorExtendDTO doctorExtendDTO = doctorClient.getDoctorExtendDTO(recipe.getDoctor());
        if (null != doctorExtendDTO && null != doctorExtendDTO.getSealData()) {
            requestSealTO.setSealBase64Str(doctorExtendDTO.getSealData());
        }
        return requestSealTO;
    }

    /**
     * 医生签名
     *
     * @param recipe
     */
    public void updateDoctorNamePdf(Recipe recipe) {
        logger.info("CreatePdfFactory updateDoctorNamePdf recipe:{}", recipe.getRecipeId());
        try {
            CreatePdfService createPdfService = createPdfService(recipe);
            byte[] data = createPdfService.queryPdfByte(recipe);
            updateDoctorNamePdf(recipe, data, createPdfService);
        } catch (Exception e) {
            logger.error("CreatePdfFactory updateDoctorNamePdf 使用平台医生部分pdf的,生成失败 recipe:{}", recipe.getRecipeId(), e);
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "医生部分pdf的生成失败");
        }

    }

    /**
     * 获取药师 pdf byte 格式
     *
     * @param recipeId 处方id
     * @return
     */
    public CaSealRequestTO queryCheckPdfByte(Integer recipeId) {
        logger.info("CreatePdfFactory queryCheckPdfByte recipeId:{}", recipeId);
        Recipe recipe = validate(recipeId);
        CreatePdfService createPdfService = createPdfService(recipe);
        CaSealRequestTO caSealRequestTO = createPdfService.queryCheckPdfByte(recipe);
        if (null == caSealRequestTO) {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "获取药师pdf_byte格式生成null");
        }
        logger.info("CreatePdfFactory queryCheckPdfByte caSealRequestTO:{}", JSON.toJSONString(caSealRequestTO));
        return caSealRequestTO;
    }


    /**
     * 药师签名
     *
     * @param recipeId
     */
    public void updateCheckNamePdf(Integer recipeId) {
        Recipe recipe = validate(recipeId);
        logger.info("CreatePdfFactory updateCheckNamePdf recipeId:{}", recipeId);
        boolean usePlatform = configurationClient.getValueBooleanCatch(recipe.getClinicOrgan(), "recipeUsePlatformCAPDF", true);
        if (!usePlatform) {
            return;
        }
        //获取签名图片
        AttachSealPicDTO sttachSealPicDTO = signManager.attachSealPic(recipe.getClinicOrgan(), recipe.getDoctor(), recipe.getChecker(), recipeId);
        String signImageId = sttachSealPicDTO.getCheckerSignImg();
        updateCheckNamePdfDefault(recipeId,signImageId);
    }


    /**
     * 默认签名
     * @param recipeId
     */
    public void updateCheckNamePdfDefault(Integer recipeId,String signImageId) {
        Recipe recipe = validate(recipeId);
        logger.info("CreatePdfFactory updateCheckNamePdfDefault recipeId:{}", recipeId);
        try {
            CreatePdfService createPdfService = createPdfService(recipe);
            String fileId = createPdfService.updateCheckNamePdf(recipe, signImageId);
            if (StringUtils.isEmpty(fileId)) {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "药师签名部分生成null");
                return;
            }
            Recipe recipeUpdate = new Recipe();
            recipeUpdate.setRecipeId(recipeId);
            recipeUpdate.setChemistSignFile(fileId);
            recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
            logger.info("CreatePdfFactory updateCheckNamePdfDefault  recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
        } catch (Exception e) {
            logger.error("CreatePdfFactory updateCheckNamePdfDefault  recipe: {}", recipe.getRecipeId(), e);
            RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "平台药师部分pdf的生成失败");
        }
    }

    /**
     * E签宝 药师签名
     *
     * @param recipeId
     */
    public void updateCheckNamePdfESign(Integer recipeId) {
        logger.info("CreatePdfFactory updateCheckNamePdfEsign recipeId:{}", recipeId);
        Recipe recipe = validate(recipeId);
        CreatePdfService createPdfService = createPdfService(recipe);
        boolean usePlatform = configurationClient.getValueBooleanCatch(recipe.getClinicOrgan(), "recipeUsePlatformCAPDF", true);
        if (!usePlatform) {
            return;
        }
        // todo 下载签名文件
        byte[] chemistSignFileByte = CreateRecipePdfUtil.signFileByte(recipe.getSignFile());

        SignRecipePdfVO pdfEsign = new SignRecipePdfVO();
        pdfEsign.setData(chemistSignFileByte);
        pdfEsign.setWidth(100f);
        pdfEsign.setFileName("recipecheck" + recipe.getRecipeId() + ".pdf");
        // todo 这个是默认的值吗
        pdfEsign.setDoctorId(recipe.getChecker());
        pdfEsign.setQrCodeSign(false);
        try {
            // todo 这个是签名对象
            byte[] data = createPdfService.updateCheckNamePdfEsign(recipeId, pdfEsign);
            if (null == data) {
                RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "药师E签宝签名部分生成null");
                return;
            }
            String fileId = CreateRecipePdfUtil.signFileByte(data, "recipecheck" + recipe.getRecipeId() + ".pdf");
            Recipe recipeUpdate = new Recipe();
            recipeUpdate.setRecipeId(recipe.getRecipeId());
            // todo 处方签名图片
            recipeUpdate.setChemistSignFile(fileId);
            recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
            logger.info("CreatePdfFactory updateCheckNamePdfEsign  recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
        } catch (Exception e) {
            logger.error("CreatePdfFactory updateCheckNamePdfEsign  recipe: {}", recipe.getRecipeId(), e);
            RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "药师E签宝签名部分生成失败");
        }
    }



    /**
     * 处方金额
     *
     * @param recipeId
     * @param recipeFee
     */
    public void updateTotalPdfExecute(Integer recipeId, BigDecimal recipeFee) {
        logger.info("CreatePdfFactory updateTotalPdfExecute recipeId:{},recipeFee:{}", recipeId, recipeFee);
        if (null == recipeFee) {
            logger.warn("CreatePdfFactory updateTotalPdfExecute recipeFee is null");
            return;
        }
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            Recipe recipe = validate(recipeId);
            CreatePdfService createPdfService = createPdfService(recipe);
            CoOrdinateVO coords = createPdfService.updateTotalPdf(recipe, recipeFee);
            logger.info("CreatePdfFactory updateTotalPdfExecute  coords ={}, recipe ={}", JSON.toJSONString(coords), JSON.toJSONString(recipe));
            if (null == coords) {
                return;
            }
            Recipe recipeUpdate = new Recipe();
            try {
                if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                    String fileId = CreateRecipePdfUtil.generateCoOrdinatePdf(recipe.getChemistSignFile(), coords);
                    recipeUpdate.setChemistSignFile(fileId);
                } else if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                    String fileId = CreateRecipePdfUtil.generateCoOrdinatePdf(recipe.getSignFile(), coords);
                    recipeUpdate.setSignFile(fileId);
                }
            } catch (Exception e) {
                logger.error("CreatePdfFactory updateTotalPdfExecute  error recipeId={}", recipeId, e);
                return;
            }finally {
                recipeUpdate.setRecipeId(recipeId);
                recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                logger.info("CreatePdfFactory updateTotalPdfExecute recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updateTotalPdfExecute pdf-处方金额追加 执行时间:{}.", elapsedTime);
            }
        });
    }

    /**
     * pdf 处方号和患者病历号
     *
     * @param recipeId
     */
    public void updateCodePdfExecute(Integer recipeId) {
        logger.info("CreatePdfFactory updateCodePdfExecute recipeId:{}", recipeId);
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            Recipe recipe = validate(recipeId);
            CreatePdfService createPdfService = createPdfService(recipe);
            try {
                String fileId = createPdfService.updateCodePdf(recipe);
                logger.info("CreatePdfFactory updateCodePdfExecute fileId ={}", fileId);
                if (StringUtils.isEmpty(fileId)) {
                    return;
                }
                Recipe recipeUpdate = new Recipe();
                recipeUpdate.setRecipeId(recipeId);
                if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                    recipeUpdate.setChemistSignFile(fileId);
                } else {
                    recipeUpdate.setSignFile(fileId);
                }
                recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                logger.info("CreatePdfFactory updateCodePdfExecute recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
            } catch (Exception e) {
                logger.error("CreatePdfFactory updateCodePdfExecute error！recipeId:{}", recipeId, e);
            }finally {
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updateCodePdfExecute pdf-处方号和患者病历号追加 执行时间:{}.", elapsedTime);
            }
        });
    }

    /**
     * 支付成功后修改pdf 添加收货人信息/煎法
     *
     * @param recipeId
     */
    public void updateAddressPdfExecute(Integer recipeId) {
        logger.info("CreatePdfFactory updateAddressPdfExecute recipeId:{}", recipeId);
        RecipeOrder order = orderDAO.getRelationOrderByRecipeId(recipeId);
        if (null == order) {
            logger.warn("CreatePdfFactory updateAddressPdfExecute   order is null  recipeId={}", recipeId);
            return;
        }
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            Recipe recipe = validate(recipeId);
            CreatePdfService createPdfService = createPdfService(recipe);
            try {
                List<CoOrdinateVO> list = createPdfService.updateAddressPdf(recipe, order, getCompleteAddress(order));
                logger.info("CreatePdfFactory updateAddressPdfExecute list ={},recipe={}", JSON.toJSONString(list), JSON.toJSONString(recipe));
                if (CollectionUtils.isEmpty(list)) {
                    return;
                }
                Recipe recipeUpdate = new Recipe();
                String fileId = null;
                if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                    fileId = CreateRecipePdfUtil.generateOrdinateList(recipe.getChemistSignFile(), list);
                    recipeUpdate.setChemistSignFile(fileId);
                } else if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                    fileId = CreateRecipePdfUtil.generateOrdinateList(recipe.getSignFile(), list);
                    recipeUpdate.setSignFile(fileId);
                }
                if (StringUtils.isNotEmpty(fileId)) {
                    recipeUpdate.setRecipeId(recipe.getRecipeId());
                    recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                    logger.info("CreatePdfFactory updateAddressPdfExecute recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
                }
            } catch (Exception e) {
                logger.error("CreatePdfFactory updateAddressPdfExecute  recipe: {}", recipe.getRecipeId(), e);
            }finally {
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updateAddressPdfExecute pdf-添加收货人信息/煎法追加 执行时间:{}.", elapsedTime);
            }
        });
    }

    /**
     * pdf 核对发药
     *
     * @param recipe 处方
     * @return
     */
    public void updateGiveUser(Recipe recipe) {
        long start = System.currentTimeMillis();
        logger.info("CreatePdfFactory updateGiveUser recipe={}", JSON.toJSONString(recipe));
        //判断发药状态
        if (StringUtils.isEmpty(recipe.getOrderCode())) {
            return;
        }
        RecipeOrder recipeOrder = orderDAO.getByOrderCode(recipe.getOrderCode());
        if (null == recipeOrder || null == recipeOrder.getDispensingTime()) {
            return;
        }
        Recipe recipeUpdate = new Recipe();
        recipeUpdate.setRecipeId(recipe.getRecipeId());
        CreatePdfService createPdfService = createPdfService(recipe);
        //写入发药时间
        CoOrdinateVO coords = createPdfService.updateDispensingTimePdf(recipe, ByteUtils.dateToSting(recipeOrder.getDispensingTime()));
        try {
            if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                String fileId = CreateRecipePdfUtil.generateCoOrdinatePdf(recipe.getChemistSignFile(), coords);
                recipeUpdate.setChemistSignFile(fileId);
                recipe.setChemistSignFile(fileId);
            } else if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                String fileId = CreateRecipePdfUtil.generateCoOrdinatePdf(recipe.getSignFile(), coords);
                recipeUpdate.setSignFile(fileId);
                recipe.setSignFile(fileId);
            }
            recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
        } catch (Exception e) {
            logger.error("CreatePdfFactory updateGiveUser updateDispensingTimePdf  recipe: {}", recipe.getRecipeId(), e);
        }
        //获取 核对发药药师签名id
        ApothecaryDTO apothecaryDTO = signManager.giveUser(recipe.getClinicOrgan(), recipe.getGiveUser(), recipe.getRecipeId());
        if (StringUtils.isEmpty(apothecaryDTO.getGiveUserSignImg())) {
            return;
        }
        //获取pdf坐标
        SignImgNode signImgNode = createPdfService.updateGiveUser(recipe);
        if (null == signImgNode) {
            return;
        }
        signImgNode.setSignImgFileId(apothecaryDTO.getGiveUserSignImg());
        try {
            if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                signImgNode.setSignFileId(recipe.getChemistSignFile());
                String fileId = CreateRecipePdfUtil.generateSignImgNode(signImgNode);
                recipeUpdate.setChemistSignFile(fileId);
            } else if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                signImgNode.setSignFileId(recipe.getSignFile());
                String fileId = CreateRecipePdfUtil.generateSignImgNode(signImgNode);
                recipeUpdate.setSignFile(fileId);
            }
            recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
        } catch (Exception e) {
            logger.error("CreatePdfFactory updateGiveUser  recipe: {}", recipe.getRecipeId(), e);
        }
        logger.info("CreatePdfFactory updateGiveUser recipeUpdate ={},执行时间:{}", JSON.toJSONString(recipeUpdate), System.currentTimeMillis() - start);
    }


    /**
     * pdf 转 图片
     *
     * @param recipeId 处方号
     * @param signImageType 签名图片类型 0 医生签名图片 1 药师签名图片
     * @return
     */
    public void updatePdfToImg(Integer recipeId, Integer signImageType) {
        logger.info("CreatePdfFactory updatePdfToImg recipeId:{}", recipeId);
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            Recipe recipe = validate(recipeId);
            try {
                String signFile = "";
                if (SignImageTypeEnum.SIGN_IMAGE_TYPE_DOCTOR.getType().equals(signImageType)) {
                    signFile = recipe.getSignFile();
                } else {
                    signFile = recipe.getChemistSignFile();
                }
                String imageFile = CreateRecipePdfUtil.updatePdfToImg(recipe.getRecipeId(), signFile);
                if (StringUtils.isNotEmpty(imageFile)) {
                    Recipe recipeUpdate = new Recipe();
                    recipeUpdate.setRecipeId(recipeId);
                    recipeUpdate.setSignImg(imageFile);
                    recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                }
            } catch (Exception e) {
                logger.error("CreatePdfFactory updatePdfToImg error", e);
                RecipeLogService.saveRecipeLog(recipeId, recipe.getStatus(), recipe.getStatus(), "pdf转图片生成失败");
            }finally {
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updatePdfToImg pdf-转图片 执行时间:{}.", elapsedTime);
            }
        });
    }

    /**
     * pdf 机构印章
     *
     * @param recipeId
     */
    public void updatesealPdfExecute(Integer recipeId) {
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            logger.info("GenerateSignetRecipePdfRunable start recipeId={}", recipeId);
            Recipe recipe = recipeDAO.get(recipeId);
            if (null == recipe || StringUtils.isEmpty(recipe.getChemistSignFile())) {
                logger.info("GenerateSignetRecipePdfRunable recipe is null");
                return;
            }
            //获取配置--机构印章
            String organSealId = configurationClient.getValueCatch(recipe.getClinicOrgan(), "organSeal", "");
            if (StringUtils.isEmpty(organSealId)) {
                logger.info("GenerateSignetRecipePdfRunable organSeal is null");
                return;
            }
            CreatePdfService createPdfService = createPdfService(recipe);
            try {
                SignImgNode signImgNode = createPdfService.updateSealPdf(recipe.getRecipeId(), organSealId, recipe.getChemistSignFile());
                String fileId = CreateRecipePdfUtil.generateSignImgNode(signImgNode);
                if (StringUtils.isEmpty(fileId)) {
                    return;
                }
                Recipe recipeUpdate = new Recipe();
                recipeUpdate.setRecipeId(recipeId);
                recipeUpdate.setChemistSignFile(fileId);
                recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                logger.info("GenerateSignetRecipePdfRunable end recipeUpdate={}", JSON.toJSONString(recipeUpdate));
            } catch (Exception e) {
                logger.error("GenerateSignetRecipePdfRunable error recipeId={}, e={}", recipeId, e);
            }finally {
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updatesealPdfExecute pdf-机构印章 执行时间:{}.", elapsedTime);
            }
        });
    }



    /**
     * pdf 监管流水号
     *
     * @param recipeId
     */
    public void updateSuperviseRecipeCodeExecute(Integer recipeId, String superviseRecipeCode) {
        logger.info("CreatePdfFactory updateSuperviseRecipeCodeExecute recipeId:{},superviseRecipeCode:{}", recipeId, superviseRecipeCode);
        RecipeBusiThreadPool.execute(() -> {
            long start = System.currentTimeMillis();
            Recipe recipe = validate(recipeId);
            CreatePdfService createPdfService = createPdfService(recipe);
            Recipe recipeUpdate = new Recipe();
            try {
                if (StringUtils.isNotEmpty(recipe.getChemistSignFile())) {
                    String fileId = createPdfService.updateSuperviseRecipeCodeExecute(recipe, recipe.getChemistSignFile(), superviseRecipeCode);
                    recipeUpdate.setChemistSignFile(fileId);
                } else if (StringUtils.isNotEmpty(recipe.getSignFile())) {
                    String fileId = createPdfService.updateSuperviseRecipeCodeExecute(recipe, recipe.getSignFile(), superviseRecipeCode);
                    recipeUpdate.setSignFile(fileId);
                }
            } catch (Exception e) {
                logger.error("CreatePdfFactory updateSuperviseRecipeCodeExecute  recipeId: {}", recipeId, e);
                return;
            }finally {
                recipeUpdate.setRecipeId(recipeId);
                recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
                logger.info("CreatePdfFactory updateSuperviseRecipeCodeExecute recipeUpdate ={}", JSON.toJSONString(recipeUpdate));
                long elapsedTime = System.currentTimeMillis() - start;
                logger.info("RecipeBusiThreadPool updateSuperviseRecipeCodeExecute pdf-监管流水号追加 执行时间:{}.", elapsedTime);
            }
        });
    }


    /**
     * 更新医生签名文件
     *
     * @param recipe
     * @param data
     * @throws Exception
     */
    private void updateDoctorNamePdf(Recipe recipe, byte[] data, CreatePdfService createPdfService) throws Exception {
        boolean usePlatform = configurationClient.getValueBooleanCatch(recipe.getClinicOrgan(), "recipeUsePlatformCAPDF", true);
        if (!usePlatform) {
            return;
        }
        //设置签名图片
        AttachSealPicDTO sttachSealPicDTO = signManager.attachSealPic(recipe.getClinicOrgan(), recipe.getDoctor(), recipe.getChecker(), recipe.getRecipeId());
        SignImgNode signImgNode = new SignImgNode();
        signImgNode.setRecipeId(recipe.getRecipeId().toString());
        signImgNode.setSignImgFileId(sttachSealPicDTO.getDoctorSignImg());
        signImgNode.setHeight(20f);
        signImgNode.setWidth(40f);
        signImgNode.setRepeatWrite(false);
        String fileId = createPdfService.updateDoctorNamePdf(data, recipe.getRecipeId(), signImgNode);
        if (StringUtils.isEmpty(fileId)) {
            RecipeLogService.saveRecipeLog(recipe.getRecipeId(), recipe.getStatus(), recipe.getStatus(), "医生部分pdf的生成null");
            return;
        }
        Recipe recipeUpdate = new Recipe();
        recipeUpdate.setRecipeId(recipe.getRecipeId());
        recipeUpdate.setSignFile(fileId);
        recipeDAO.updateNonNullFieldByPrimaryKey(recipeUpdate);
        logger.info("CreatePdfFactory updateDoctorNamePdf recipeUpdate={}", JSON.toJSONString(recipeUpdate));
    }


    private Recipe validate(Integer recipeId) {
        if (ValidateUtil.validateObjects(recipeId)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "参数错误");
        }
        Recipe recipe = recipeDAO.getByRecipeId(recipeId);
        if (ValidateUtil.validateObjects(recipe, recipe.getRecipeId(), recipe.getClinicOrgan())) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "参数错误");
        }
        return recipe;
    }

    /**
     * 判断使用那个实现类
     *
     * @param recipe
     * @return
     */
    private CreatePdfService createPdfService(Recipe recipe) {
        String organSealId;
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            organSealId = configurationClient.getValueCatch(recipe.getClinicOrgan(), OperationConstant.OP_CONFIG_PDF_CHINA, "");
        } else {
            organSealId = configurationClient.getValueCatch(recipe.getClinicOrgan(), OperationConstant.OP_CONFIG_PDF, "");
        }

        CreatePdfService createPdfService;
        if (StringUtils.isNotEmpty(organSealId)) {
            createPdfService = customCreatePdfServiceImpl;
        } else {
            createPdfService = platformCreatePdfServiceImpl;
        }
        return createPdfService;
    }

    /**
     * 地址
     *
     * @param order
     * @return
     */
    private String getCompleteAddress(RecipeOrder order) {
        StringBuilder address = new StringBuilder();
        if (null != order) {
            address.append(getDictionary("eh.base.dictionary.AddrArea", order.getAddress1()));
            address.append(getDictionary("eh.base.dictionary.AddrArea", order.getAddress2()));
            address.append(getDictionary("eh.base.dictionary.AddrArea", order.getAddress3()));
            address.append(StringUtils.isEmpty(order.getAddress4()) ? "" : order.getAddress4());
        }
        return address.toString();
    }

}
