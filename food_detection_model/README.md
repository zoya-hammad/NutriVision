# Food Detection Model

This project implements a food detection system using deep learning models (VGG16, Xception, and EfficientNet) to classify various food items.

## Project Structure

```
food_detection_model/
├── testing/
│   ├── train_models.py
│   └── test_models.py
├── data preprocessing/
├── dataset/
│   └── processed_dataset/
├── base metrics/
└── trained_models/
```

## Setup and Installation

1. Install required packages:
```bash
pip install torch torchvision pillow numpy scikit-learn matplotlib seaborn
```

2. For GPU support, ensure you have CUDA installed.

## Training

To train the models:
```bash
python testing/train_models.py
```

This will train three models:
- VGG16
- Xception
- EfficientNet

## Testing

To evaluate the models:
```bash
python testing/test_models.py
```

## Results

The testing script generates:
- Confusion matrices for each model
- Per-class accuracy plots
- Model comparison visualization

## Google Colab Usage

1. Mount Google Drive:
```python
from google.colab import drive
drive.mount('/content/drive')
```

2. Upload and extract the project:
```python
!unzip food_detection_model.zip -d /content/drive/MyDrive/NutriVision
```

3. Run training/testing scripts as mentioned above.

## Model Files

Trained models are saved in the `trained_models/` directory with timestamps in their filenames.

## License

[Your License Here] 