import tensorflow as tf
from tensorflow.keras.applications import Xception
from tensorflow.keras.layers import Dense, GlobalAveragePooling2D
from tensorflow.keras.models import Model
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np
import os
from datetime import datetime
from sklearn.metrics import classification_report, confusion_matrix
from preprocessing import train_data, validation_data, test_data, class_names

# Configuration
IMAGE_SIZE = (299, 299)  # Xception requires 299x299 input
BATCH_SIZE = 32
RESULTS_DIR = "xception_results"
os.makedirs(RESULTS_DIR, exist_ok=True)

# Data Preparation - Apply Xception specific preprocessing
def prepare_data(image, label):
    image = tf.image.resize(image, IMAGE_SIZE)
    image = tf.keras.applications.xception.preprocess_input(image)
    return image, label

train_data = train_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)
validation_data = validation_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)
test_data = test_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)

# Xception Model with Transfer Learning
base_model = Xception(
    weights='imagenet',
    include_top=False,
    input_shape=(*IMAGE_SIZE, 3)
)

# Freeze all layers
base_model.trainable = False

# Add custom classification head
x = GlobalAveragePooling2D()(base_model.output)
predictions = Dense(len(class_names), activation='softmax')(x)
model = Model(inputs=base_model.input, outputs=predictions)

model.compile(
    optimizer='adam',
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

# Training
history = model.fit(
    train_data,
    validation_data=validation_data,
    epochs=15,
    verbose=1
)

# Evaluation and Visualization (same as VGG16)
def evaluate_model(model, dataset, class_names):
    y_true, y_pred = [], []
    for images, labels in dataset:
        y_true.extend(tf.argmax(labels, axis=1).numpy())
        y_pred.extend(tf.argmax(model.predict(images, verbose=0), axis=1))
    
    # Classification report
    report = classification_report(y_true, y_pred, target_names=class_names, output_dict=True)
    pd.DataFrame(report).transpose().to_csv(f"{RESULTS_DIR}/classification_report.csv")
    
    # Confusion matrix
    plt.figure(figsize=(10, 8))
    cm = confusion_matrix(y_true, y_pred)
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=class_names, yticklabels=class_names)
    plt.title('Confusion Matrix')
    plt.xlabel('Predicted')
    plt.ylabel('Actual')
    plt.xticks(rotation=45, ha='right')
    plt.yticks(rotation=0)
    plt.tight_layout()
    plt.savefig(f"{RESULTS_DIR}/confusion_matrix.png")
    plt.close()
    
    return report

report = evaluate_model(model, test_data, class_names)

def plot_history(history):
    plt.figure(figsize=(12, 4))
    
    plt.subplot(1, 2, 1)
    plt.plot(history.history['accuracy'], label='Train Accuracy')
    plt.plot(history.history['val_accuracy'], label='Validation Accuracy')
    plt.title('Model Accuracy')
    plt.ylabel('Accuracy')
    plt.xlabel('Epoch')
    plt.legend()
    
    plt.subplot(1, 2, 2)
    plt.plot(history.history['loss'], label='Train Loss')
    plt.plot(history.history['val_loss'], label='Validation Loss')
    plt.title('Model Loss')
    plt.ylabel('Loss')
    plt.xlabel('Epoch')
    plt.legend()
    
    plt.tight_layout()
    plt.savefig(f"{RESULTS_DIR}/training_history.png")
    plt.close()

plot_history(history)

def save_metrics(history, report):
    metrics = {
        'training_accuracy': history.history['accuracy'][-1],
        'validation_accuracy': history.history['val_accuracy'][-1],
        'test_accuracy': report['accuracy'],
        'average_precision': report['macro avg']['precision'],
        'average_recall': report['macro avg']['recall'],
        'average_f1': report['macro avg']['f1-score'],
        'timestamp': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    }
    pd.DataFrame([metrics]).to_csv(f"{RESULTS_DIR}/xception_metrics.csv", index=False)

save_metrics(history, report)

# Save model
model.save(f"{RESULTS_DIR}/xception_model.keras")

print("\nXception model evaluation results saved in 'xception_results' directory:")
print(f"- classification_report.csv: Detailed class-wise metrics")
print(f"- confusion_matrix.png: Visual prediction breakdown")
print(f"- training_history.png: Accuracy and loss curves")
print(f"- xception_metrics.csv: Key performance metrics")
print(f"- xception_model.keras: Saved model weights")