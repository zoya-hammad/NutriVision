import tensorflow as tf
from tensorflow.keras.applications import EfficientNetB0
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import numpy as np
import os
from datetime import datetime
from sklearn.metrics import classification_report, confusion_matrix
from preprocessing import train_data, validation_data, test_data, class_names


IMAGE_SIZE = (224, 224)
BATCH_SIZE = 32
RESULTS_DIR = "efficientnet_results"
os.makedirs(RESULTS_DIR, exist_ok=True)

# Applying EfficientNet specific preprocessing
def prepare_data(image, label):
    image = tf.image.resize(image, IMAGE_SIZE) 
    image = tf.keras.applications.efficientnet.preprocess_input(image)  # EfficientNet preprocessing
    return image, label

train_data = train_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)
validation_data = validation_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)
test_data = test_data.map(prepare_data).prefetch(tf.data.AUTOTUNE)

# EfficientNetB0 Model
base_model = EfficientNetB0(
    weights='imagenet',
    include_top=False,
    input_shape=(*IMAGE_SIZE, 3)
)

# Freeze all layers
base_model.trainable = False

# Custom classification head
x = tf.keras.layers.GlobalAveragePooling2D()(base_model.output)  # More efficient than Flatten for EfficientNet
x = tf.keras.layers.Dense(256, activation='relu')(x)  # Additional dense layer
predictions = tf.keras.layers.Dense(len(class_names), activation='softmax')(x)
model = tf.keras.Model(inputs=base_model.input, outputs=predictions)

model.compile(
    optimizer=tf.keras.optimizers.Adam(learning_rate=1e-3),
    loss='categorical_crossentropy',
    metrics=['accuracy']
)

# Callbacks
callbacks = [
    tf.keras.callbacks.EarlyStopping(patience=5, restore_best_weights=True),
    tf.keras.callbacks.ModelCheckpoint(
        f"{RESULTS_DIR}/best_model.keras",
        save_best_only=True,
        monitor='val_accuracy'
    ),
    tf.keras.callbacks.ReduceLROnPlateau(
        monitor='val_loss',
        factor=0.1,
        patience=3
    )
]

# Training
history = model.fit(
    train_data,
    validation_data=validation_data,
    epochs=15,
    callbacks=callbacks,
    verbose=1
)

# Evaluation and Visualization
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
    pd.DataFrame([metrics]).to_csv(f"{RESULTS_DIR}/efficientnet_metrics.csv", index=False)

save_metrics(history, report)

# final model
model.save(f"{RESULTS_DIR}/efficientnet_model.keras")

print("\nModel evaluation results saved in 'efficientnet_results' directory:")
print(f"- classification_report.csv: Detailed class-wise metrics")
print(f"- confusion_matrix.png: Visual prediction breakdown")
print(f"- training_history.png: Accuracy and loss curves")
print(f"- efficientnet_metrics.csv: Key performance metrics")
print(f"- efficientnet_model.keras: Saved model weights")
print(f"- best_model.keras: Best model weights during training")