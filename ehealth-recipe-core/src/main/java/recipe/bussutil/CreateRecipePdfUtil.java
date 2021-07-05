package recipe.bussutil;

import com.alibaba.fastjson.JSON;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.*;
import com.ngari.base.esign.model.CoOrdinateVO;
import com.ngari.upload.service.IFileUploadService;
import ctd.mvc.upload.FileMetaRecord;
import ctd.mvc.upload.FileService;
import ctd.util.JSONUtils;
import lombok.Cleanup;
import org.apache.commons.lang3.StringUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.bouncycastle.util.encoders.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import recipe.ApplicationUtils;
import recipe.third.IFileDownloadService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.util.Date;
import java.util.List;

/**
 * created by shiyuping on 2019/10/18
 */
public class CreateRecipePdfUtil {
    private static final Logger logger = LoggerFactory.getLogger(CreateRecipePdfUtil.class);
    private static final IFileDownloadService fileDownloadService = ApplicationUtils.getBaseService(IFileDownloadService.class);
    private static final IFileUploadService fileUploadService = ApplicationUtils.getBaseService(IFileUploadService.class);

    /**
     * 通用 写入特殊节点信息
     *
     * @param pdfId     pdfId
     * @param decoction 特殊节点写入
     */
    public static String generateCoOrdinatePdf(String pdfId, CoOrdinateVO decoction) throws IOException, DocumentException {
        logger.info("generateCoOrdinatePdf pdfId={}, decoction={}", pdfId, decoction);
        if (StringUtils.isEmpty(pdfId) || null == decoction) {
            return null;
        }
        FileMetaRecord fileMetaRecord = fileDownloadService.downloadAsRecord(pdfId);
        if (fileMetaRecord != null) {
            File file = new File(fileMetaRecord.getFileName());
            @Cleanup OutputStream output = new FileOutputStream(file);
            @Cleanup InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
            PdfReader reader = new PdfReader(input);
            PdfStamper stamper = new PdfStamper(reader, output);
            try {
                addTextForPdf(stamper, decoction);
            } catch (Exception e) {
                logger.error("generateCoOrdinatePdf error", e);
            } finally {
                stamper.close();
                reader.close();
            }
            //上传pdf文件
            byte[] bytes = File2byte(file);
            String fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileMetaRecord.getFileName());
            //删除本地文件
            file.delete();
            return fileId;
        }
        return null;
    }

    /**
     * 通用 根据 x，y坐标写入 图片放置
     *
     * @param signImgNode
     * @return
     */
    public static String generateSignImgNode(SignImgNode signImgNode) {
        if (StringUtils.isAnyEmpty(signImgNode.getRecipeId(), signImgNode.getSignImgFileId(), signImgNode.getSignImgId())) {
            return null;
        }
        //获取图片
        byte[] doctorSignImageByte = fileDownloadService.downloadAsByte(signImgNode.getSignImgFileId());
        File giveUserImage = new File(signImgNode.getSignImgId() + System.currentTimeMillis() + ".png");
        getFileByBytes(doctorSignImageByte, giveUserImage);
        String fileId = null;
        try {
            URL url = giveUserImage.toURI().toURL();
            //获取pdf
            byte[] signFileByte;
            if (StringUtils.isNotEmpty(signImgNode.getSignFileId())) {
                signFileByte = fileDownloadService.downloadAsByte(signImgNode.getSignFileId());
            } else {
                signFileByte = signImgNode.getSignFileData();
            }
            File signFilePDF = new File(signImgNode.getRecipeId() + System.currentTimeMillis() + ".pdf");
            @Cleanup InputStream input = new ByteArrayInputStream(signFileByte);
            @Cleanup OutputStream output = new FileOutputStream(signFilePDF);
            addBarCodeImgForRecipePdfByCoordinates(input, output, url, signImgNode.getWidth(), signImgNode.getHeight(),
                    signImgNode.getX(), signImgNode.getY(), signImgNode.getRepeatWrite());
            //上传pdf文件
            byte[] bytes = File2byte(signFilePDF);
            fileId = fileUploadService.uploadFileWithoutUrt(bytes, signFilePDF.getName());
            //删除本地文件
            signFilePDF.delete();
        } catch (Exception e) {
            logger.warn("CreateRecipePdfUtil giveUserUpdate error", e);
        }
        giveUserImage.delete();
        return fileId;
    }


    /**
     * 处方签pdf添加收货人信息
     *
     * @param pdfId
     * @param receiver
     * @param recMobile
     * @param completeAddress
     * @param height
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static String generateReceiverInfoRecipePdf(String pdfId, String receiver, String recMobile, String completeAddress, Integer height, CoOrdinateVO decoction) throws Exception {
        logger.info("generateReceiverInfoRecipePdf pdfId={}, receiver={} ,recMobile={} ,completeAddress={}", pdfId, receiver, recMobile, completeAddress);
        @Cleanup InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
        FileMetaRecord fileMetaRecord = fileDownloadService.downloadAsRecord(pdfId);
        String fileId = null;
        if (fileMetaRecord != null) {
            File file = new File(fileMetaRecord.getFileName());
            @Cleanup OutputStream output = new FileOutputStream(file);
            PdfReader reader = new PdfReader(input);
            PdfStamper stamper = new PdfStamper(reader, output);
            //煎法
            if (null != decoction) {
                addTextForPdf(stamper, decoction);
            }
            //添加接收人信息
            addReceiverInfoRecipePdf(stamper, receiver, recMobile, completeAddress, height);
            stamper.close();
            reader.close();
            //上传pdf文件
            byte[] bytes = File2byte(file);
            fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileMetaRecord.getFileName());
            //删除本地文件
            file.delete();
        }
        return fileId;
    }


    /**
     * 处方pdf添加处方号和患者病历号
     *
     * @param pdfId
     * @param coOrdinateList
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static String generateRecipeCodeAndPatientIdForRecipePdf(String pdfId, List<CoOrdinateVO> coOrdinateList, String barcode) throws Exception {
        logger.info("generateRecipeCodeAndPatientIdRecipePdf pdfId={}, coOrdinateList={} ", pdfId, coOrdinateList);
        @Cleanup InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
        FileMetaRecord fileMetaRecord = fileDownloadService.downloadAsRecord(pdfId);
        String fileId = null;
        if (fileMetaRecord != null) {
            File file = new File(fileMetaRecord.getFileName());
            @Cleanup OutputStream output = new FileOutputStream(file);
            if (StringUtils.isNotEmpty(barcode)) {
                File barCodeFile = BarCodeUtil.generateFile(barcode, "barcode.png");
                //获取图片url
                URL url = barCodeFile.toURI().toURL();
                //添加图片
                addBarCodeImgForRecipePdf(input, output, url, coOrdinateList);
                barCodeFile.delete();
            } else {
                //处方pdf添加处方号和患者病历号
                addRecipeCodeAndPatientIdForRecipePdf(input, output, coOrdinateList);
            }
            //上传pdf文件
            byte[] bytes = File2byte(file);
            fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileMetaRecord.getFileName());
            //删除本地文件
            file.delete();
        }
        return fileId;
    }

    /**
     * 取药标签
     *
     * @param pdfId
     * @return
     * @throws Exception
     */
    public static String transPdfIdForRecipePdf(String pdfId) throws Exception {
        @Cleanup InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
        FileMetaRecord fileMetaRecord = fileDownloadService.downloadAsRecord(pdfId);
        String fileId = null;
        if (fileMetaRecord != null) {
            File file = new File(fileMetaRecord.getFileName());
            @Cleanup OutputStream output = new FileOutputStream(file);
            //获取图片url
            URL url = CreateRecipePdfUtil.class.getClassLoader().getResource("drug.png");
            //添加图片
            addBarCodeImgForRecipePdfByCoordinates(input, output, url, null, null, 250, 500, false);
            //上传pdf文件
            byte[] bytes = File2byte(file);
            fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileMetaRecord.getFileName());
            //删除本地文件
            file.delete();
        }
        return fileId;
    }

    /**
     * 上传 pdf签名图片格式
     *
     * @param recipeId 处方id
     * @param pdfId
     * @return
     * @throws Exception
     */
    public static String updatePdfToImg(Integer recipeId, String pdfId) throws Exception {
        //获取pdf
        byte[] doctorSignImageByte = fileDownloadService.downloadAsByte(pdfId);
        File giveUserImage = new File("recipe_" + recipeId + ".pdf");
        getFileByBytes(doctorSignImageByte, giveUserImage);
        String fileId = null;
        try {
            //pdf转图片
            PDDocument pdDocument = PDDocument.load(doctorSignImageByte);
            PDFRenderer renderer = new PDFRenderer(pdDocument);
            BufferedImage image = renderer.renderImageWithDPI(0, 150);
            //获取图片文件id
            File imageFile = new File("recipe_" + recipeId + ".jpeg");
            ImageIO.write(image, "jpeg", imageFile);
            fileId = uploadImage(imageFile, imageFile.getName());
            imageFile.delete();
        } catch (IOException e) {
            logger.error("CreateRecipePdfUtil updatePdfToImg error ", e);
        }
        giveUserImage.delete();
        return fileId;
    }


    /**
     * 所有ca模式医生签名完成后添加水印
     *
     * @param pdfId
     * @param waterPrintText
     * @return
     * @throws IOException
     * @throws DocumentException
     */
    public static String generateWaterPrintRecipePdf(String pdfId, String waterPrintText) throws IOException, DocumentException {
        FileMetaRecord fileMetaRecord = fileDownloadService.downloadAsRecord(pdfId);
        String fileId = null;
        if (fileMetaRecord != null) {
            //因为导入包不同，放在此类调用一直报错，所以addWaterPrintForRecipePdf放在新建工具类
            byte[] bytes = CreateRecipePdfUtilByLowagie.addWaterPrintForRecipePdf(fileDownloadService.downloadAsByte(pdfId), waterPrintText);
            fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileMetaRecord.getFileName());
        }
        logger.info("generateWaterPrintRecipePdf newFileId:{}", fileId);
        return fileId;
    }


    /**
     * 下载oss服务器上的签名文件
     *
     * @param signFile ossId
     * @return
     */
    public static String signFileByte(String signFile) {
        byte[] signFileByte = fileDownloadService.downloadAsByte(signFile);
        if (null == signFileByte) {
            return "";
        }
        return new String(Base64.encode(signFileByte));
    }

    /**
     * 上传文件到oss服务器
     *
     * @param bytes    文件
     * @param fileName 文件名
     * @return
     */
    public static String signFileByte(byte[] bytes, String fileName) {
        String fileId = fileUploadService.uploadFileWithoutUrt(bytes, fileName);
        if (null == fileId) {
            return "";
        }
        return fileId;
    }


    /**
     * 读取pdf模板
     * pdf 读取输出流 方法
     *
     * @param generatePdfList
     */
    public static byte[] generateTemplatePdf(Integer recipeId, String pdfId, List<WordToPdfBean> generatePdfList, List<CoOrdinateVO> ordinateList) throws Exception {
        @Cleanup InputStream input = new ByteArrayInputStream(fileDownloadService.downloadAsByte(pdfId));
        @Cleanup ByteArrayOutputStream bos = new ByteArrayOutputStream();
        PdfReader reader = new PdfReader(input);
        PdfStamper stamper = new PdfStamper(reader, bos);
        AcroFields form = stamper.getAcroFields();
        form.addSubstitutionFont(BaseFont.createFont("STSongStd-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED));
        //模版填充 数据方法
        templatePdf(generatePdfList, form, ordinateList);
        // 如果为false，生成的PDF文件可以编辑，如果为true，生成的PDF文件不可以编辑
        stamper.setFormFlattening(true);
        stamper.close();

        //拷贝模版生成新pdf
        String fileName = "recipe_" + recipeId + ".pdf";
        File file = new File(fileName);
        @Cleanup OutputStream output = new FileOutputStream(file);
        Document doc = new Document();
        PdfSmartCopy copy = new PdfSmartCopy(doc, output);
        doc.open();
        PdfImportedPage importPage = copy.getImportedPage(new PdfReader(bos.toByteArray()), 1);
        copy.addPage(importPage);
        doc.close();
        byte[] bytes = File2byte(file);
        file.delete();
        return bytes;
    }

    /**
     * 模版填充 数据方法
     *
     * @param list 模版数据对象
     * @param form 模版
     */
    private static void templatePdf(List<WordToPdfBean> list, AcroFields form, List<CoOrdinateVO> ordinateList) throws IOException, DocumentException {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        for (WordToPdfBean wordToPdf : list) {
            try {
                String key = wordToPdf.getKey();
                String value = wordToPdf.getValue();
                if (1 == wordToPdf.getType()) {
                    //文字类的内容处理
                    form.setField(key, value);
                } else {
                    //将图片写入指定的field
                    Image image = Image.getInstance(value);
                    PushbuttonField pb = form.getNewPushbuttonFromField(key);
                    pb.setImage(image);
                    form.replacePushbuttonField(key, pb.getField());
                }
            } catch (Exception e) {
                logger.error("CreateRecipePdfUtil templatePdf error ", e);
            }
        }

        //定位某个表单字段坐标
        ordinateList.forEach(a -> {
            List<AcroFields.FieldPosition> pos = form.getFieldPositions(a.getName());
            AcroFields.FieldPosition pitem = pos.get(0);
            Rectangle pRectangle = pitem.position;
            a.setX((int) pRectangle.getLeft());
            a.setY((int) pRectangle.getBottom());
        });
    }


    /**
     * 上传图片文件到oss服务器
     *
     * @param file     图片文件
     * @param fileName 文件名
     * @return
     */
    private static String uploadImage(File file, String fileName) {
        try {
            FileMetaRecord meta = new FileMetaRecord();
            meta.setManageUnit("eh");
            meta.setLastModify(new Date());
            meta.setUploadTime(new Date());
            meta.setMode(0);
            meta.setCatalog("other-doc");
            meta.setContentType("image/jpeg");
            meta.setFileName(fileName);
            meta.setFileSize(file.length());
            logger.info("uploadPicture.meta=[{}]", JSONUtils.toString(meta));
            FileService.instance().upload(meta, file);
            return meta.getFileId();
        } catch (Exception e) {
            logger.error("uploadPicture uploadRecipeFile exception:" + e.getMessage());
        }
        return null;
    }

    /**
     * 处方签pdf添加收货人信息
     *
     * @param stamper
     * @param receiver
     * @param recMobile
     * @param completeAddress
     * @param height
     * @throws IOException
     * @throws DocumentException
     */
    private static void addReceiverInfoRecipePdf(PdfStamper stamper, String receiver, String recMobile, String completeAddress, Integer height) throws IOException, DocumentException {
        PdfContentByte page = stamper.getOverContent(1);
        //将文字贴入pdf
        BaseFont bf = BaseFont.createFont(ClassLoader.getSystemResource("recipe/font/simhei.ttf").toString(), BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
        page.beginText();
        page.setColorFill(BaseColor.BLACK);
        page.setFontAndSize(bf, 10);
        page.setTextMatrix(10, height);
        page.showText("收货人姓名：" + receiver);
        page.setTextMatrix(149, height);
        page.showText("收货人电话：" + recMobile);
        page.setTextMatrix(10, height - 12);
        page.showText("收货人地址：" + completeAddress);
        page.endText();
    }


    /**
     * 条形码 处方号和患者病历号
     *
     * @param input
     * @param output
     * @param url
     * @throws Exception
     */
    private static void addBarCodeImgForRecipePdf(InputStream input, OutputStream output, URL url, List<CoOrdinateVO> coOrdinateList) throws Exception {
        PdfReader reader = new PdfReader(input);
        PdfStamper stamper = new PdfStamper(reader, output);
        PdfContentByte page = stamper.getOverContent(1);
        //将图片贴入pdf
        Image image = Image.getInstance(url);
        image.setAbsolutePosition(10, 560);
        image.scaleToFit(110, 20);
        page.addImage(image);
        //处方pdf添加处方号和患者病历号
        addRecipeCodeAndPatientIdForRecipePdf(coOrdinateList, stamper);
        stamper.close();
        reader.close();
    }


    /**
     * 修改处方单号和患者病历号
     *
     * @param input
     * @param output
     * @param coOrdinateList
     * @throws IOException
     * @throws DocumentException
     */
    private static void addRecipeCodeAndPatientIdForRecipePdf(InputStream input, OutputStream output, List<CoOrdinateVO> coOrdinateList) throws Exception {
        PdfReader reader = new PdfReader(input);
        PdfStamper stamper = new PdfStamper(reader, output);
        addRecipeCodeAndPatientIdForRecipePdf(coOrdinateList, stamper);
        stamper.close();
        reader.close();
    }

    private static void addRecipeCodeAndPatientIdForRecipePdf(List<CoOrdinateVO> coOrdinateList, PdfStamper stamper) {
        if (CollectionUtils.isEmpty(coOrdinateList)) {
            return;
        }
        coOrdinateList.forEach(a -> {
            try {
                addTextForPdf(stamper, a);
            } catch (Exception e) {
                logger.error("addRecipeCodeAndPatientIdForRecipePdf error", e);
            }
        });
    }


    private static byte[] File2byte(File tradeFile) {
        byte[] buffer = null;
        try {
            FileInputStream fis = new FileInputStream(tradeFile);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[1024];
            int n;
            while ((n = fis.read(b)) != -1) {
                bos.write(b, 0, n);
            }
            fis.close();
            bos.close();
            buffer = bos.toByteArray();
        } catch (Exception e) {
            logger.error("File2byte e", e);
        }
        return buffer;
    }


    /**
     * 生成本地文件
     *
     * @param bytes
     * @param file
     */
    private static void getFileByBytes(byte[] bytes, File file) {
        try {
            //输出流
            @Cleanup FileOutputStream fos = new FileOutputStream(file);
            //缓冲流
            @Cleanup BufferedOutputStream bos = new BufferedOutputStream(fos);
            bos.write(bytes);
        } catch (Exception e) {
            logger.info("getFileByBytes error", e);
        }
    }

    /**
     * 根据 x，y坐标放置 图片
     *
     * @param input     输入流
     * @param output    输出流
     * @param url       图片地址
     * @param newWidth  显示宽度
     * @param newHeight 显示高度
     * @param xPoint    定位坐标x
     * @param yPoint    定位坐标y
     * @throws Exception
     */
    private static void addBarCodeImgForRecipePdfByCoordinates(InputStream input, OutputStream output, URL url
            , Float newWidth, Float newHeight, float xPoint, float yPoint, Boolean repeatWrite) throws Exception {
        PdfReader reader = new PdfReader(input);
        PdfStamper stamper = new PdfStamper(reader, output);
        PdfContentByte page = stamper.getOverContent(1);
        if (repeatWrite) {
            //添加空白覆盖
            page.saveState();
            page.setColorFill(BaseColor.WHITE);
            page.rectangle(xPoint, yPoint, newWidth, newHeight);
            page.fill();
            page.restoreState();
        }

        //将图片贴入pdf
        Image image = Image.getInstance(url);
        if (null != newWidth) {
            //显示的大小
            image.scaleAbsolute(newWidth, newHeight);
        }
        //设置图片在页面中的坐标
        image.setAbsolutePosition(xPoint, yPoint);
        page.addImage(image);
        stamper.close();
        reader.close();
    }

    /**
     * 根据 x，y坐标写入text文本内容
     *
     * @param stamper
     * @param decoction 坐标
     * @throws Exception
     */
    private static void addTextForPdf(PdfStamper stamper, CoOrdinateVO decoction) throws Exception {
        logger.info("addTextForRecipePdf text:{}", JSON.toJSONString(decoction));
        PdfContentByte page = stamper.getOverContent(1);
        BaseFont bf = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.EMBEDDED);
        if (null != decoction.getRepeatWrite() && decoction.getRepeatWrite()) {
            //添加空白覆盖
            page.saveState();
            page.setColorFill(BaseColor.WHITE);
            page.rectangle(decoction.getX(), decoction.getY(), 120, 14);
            page.fill();
            page.restoreState();
        }
        //添加文本块
        page.beginText();
        page.setColorFill(BaseColor.BLACK);
        page.setFontAndSize(bf, 10);
        page.setTextMatrix(decoction.getX(), decoction.getY());
        page.showText(decoction.getValue());
        page.endText();
    }


}
