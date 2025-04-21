package com.example.visionandroid;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect; // Cần dùng cho getTextBounds
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import androidx.core.content.ContextCompat;

import org.tensorflow.lite.support.label.Category;
import org.tensorflow.lite.task.vision.detector.Detection; // Import lớp Detection
import org.tensorflow.lite.task.vision.*; // Import lớp Category

import java.util.LinkedList;
import java.util.List;
import java.util.Locale; // Cần cho String.format

public class OverlayView extends View {

    // Danh sách lưu kết quả nhận diện
    private List<Detection> detectionResults = new LinkedList<>();

    // Các đối tượng Paint để vẽ
    private final Paint boxPaint = new Paint();
    private final Paint textBackgroundPaint = new Paint();
    private final Paint textPaint = new Paint();

    // Hệ số tỉ lệ
    private float scaleFactor = 1.0f;

    // RectF tái sử dụng để vẽ
    private final RectF drawingRect = new RectF();

    // Hằng số
    private static final int BOUNDING_RECT_TEXT_PADDING = 8;
    private static final String TAG = "OverlayView";

    // --- Constructors ---
    public OverlayView(Context context) {
        super(context);
        initPaints();
    }

    public OverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initPaints();
    }

    public OverlayView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initPaints();
    }

    // ---- Các phương thức sẽ được thêm vào bên dưới ----
    private void initPaints() {
        // Cấu hình Paint cho Text
        textPaint.setColor(Color.WHITE);
        textPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextSize(50f); // Điều chỉnh kích thước

        // Cấu hình Paint cho nền Text
        textBackgroundPaint.setColor(Color.BLACK);
        textBackgroundPaint.setStyle(Paint.Style.FILL);
        textBackgroundPaint.setAlpha(160); // Độ trong suốt (0-255)

        // Cấu hình Paint cho Bounding Box
        boxPaint.setColor(ContextCompat.getColor(getContext(), R.color.bounding_box_color)); // Lấy màu từ colors.xml
        boxPaint.setStrokeWidth(6F); // Độ dày nét vẽ
        boxPaint.setStyle(Paint.Style.STROKE); // Chỉ vẽ đường viền
    }

    /**
     * Nhận kết quả nhận diện mới và kích thước ảnh gốc, sau đó yêu cầu vẽ lại View.
     * @param detectionResults Danh sách các đối tượng được phát hiện.
     * @param imageSourceHeight Chiều cao của ảnh gốc được dùng để nhận diện.
     * @param imageSourceWidth Chiều rộng của ảnh gốc được dùng để nhận diện.
     */
    public void setResults(List<Detection> detectionResults, int imageSourceHeight, int imageSourceWidth) {
        // Quan trọng: Nên tạo danh sách mới để tránh lỗi nếu danh sách gốc bị thay đổi ở luồng khác
        // Hoặc đảm bảo việc cập nhật và vẽ luôn diễn ra trên cùng một luồng (ví dụ: UI thread).
        // Cách đơn giản là tạo bản sao:
        // this.detectionResults = new LinkedList<>(detectionResults);
        // Hoặc nếu chắc chắn về luồng:
        this.detectionResults = detectionResults;


        // Tính toán lại scaleFactor
        // Cần ép kiểu (float) để đảm bảo phép chia là số thực
        scaleFactor = Math.max(
                (float) getWidth() / imageSourceWidth,
                (float) getHeight() / imageSourceHeight
        );

        // Yêu cầu hệ thống vẽ lại View
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); // Gọi hàm của lớp cha

        // Vòng lặp duyệt qua từng kết quả
        for (Detection result : detectionResults) {
            // Lấy bounding box - Giả định TFLite Task Library trả về RectF
            // Kiểm tra null nếu API có thể trả về null
            RectF boundingBox = result.getBoundingBox();
            if (boundingBox == null) continue; // Bỏ qua nếu không có bounding box

            // Tính toán tọa độ đã scale
            float scaledLeft = boundingBox.left * scaleFactor;
            float scaledTop = boundingBox.top * scaleFactor;
            float scaledRight = boundingBox.right * scaleFactor;
            float scaledBottom = boundingBox.bottom * scaleFactor;

            // Đặt tọa độ vào RectF tái sử dụng
            drawingRect.set(scaledLeft, scaledTop, scaledRight, scaledBottom);

            // Vẽ bounding box
            canvas.drawRect(drawingRect, boxPaint);

            // Chỉ vẽ text nếu có categories
            List<Category> categories = result.getCategories();
            if (categories != null && !categories.isEmpty()) {
                // Lấy category đầu tiên (thường có score cao nhất)
                Category topCategory = categories.get(0);
                // Giả định có getLabel() và getScore()
                String label = topCategory.getLabel() != null ? topCategory.getLabel() : "N/A";
                float score = topCategory.getScore();
                String drawableText = String.format(Locale.US, "%s %.2f", label, score); // Format: "label score"

                // --- Vẽ nền cho text ---
                float textWidth = textPaint.measureText(drawableText);
                float textHeight = textPaint.getTextSize(); // Chiều cao ước lượng

                // Cần một đối tượng Rect để chứa text bounds
                Rect textBounds = new Rect();
                textPaint.getTextBounds(drawableText, 0, drawableText.length(), textBounds);
                // Chiều cao chính xác hơn từ getTextBounds nếu cần
                // textHeight = textBounds.height();

                // Vẽ hình chữ nhật nền
                canvas.drawRect(
                        scaledLeft,                                     // X bắt đầu
                        scaledTop - textHeight - BOUNDING_RECT_TEXT_PADDING * 2 , // Y bắt đầu (phía trên box, thêm padding)
                        scaledLeft + textWidth + BOUNDING_RECT_TEXT_PADDING * 2,  // X kết thúc (thêm padding)
                        scaledTop,                                      // Y kết thúc
                        textBackgroundPaint                             // Dùng Paint nền
                );

                // --- Vẽ text lên trên nền ---
                canvas.drawText(
                        drawableText,                                   // Chuỗi
                        scaledLeft + BOUNDING_RECT_TEXT_PADDING,        // X (thêm padding)
                        scaledTop - BOUNDING_RECT_TEXT_PADDING,         // Y (phía trên box, trừ padding)
                        textPaint                                       // Dùng Paint chữ
                );
            }
        }
    }

    /**
     * Xóa các kết quả nhận diện và yêu cầu vẽ lại View (trạng thái trống).
     */
    public void clear() {
        // Tạo danh sách mới để đảm bảo an toàn luồng hoặc nếu muốn xóa hoàn toàn tham chiếu cũ
        this.detectionResults = new LinkedList<>();
        // Hoặc nếu quản lý luồng tốt:
        // this.detectionResults.clear();
        invalidate(); // Yêu cầu vẽ lại
    }
}