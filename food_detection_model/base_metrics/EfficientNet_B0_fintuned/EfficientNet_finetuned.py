import tensorflow as tf
from tensorflow.keras.models import load_model
from tensorflow.keras.applications import EfficientNetB0
from tensorflow.keras import metrics
import matplotlib.pyplot as plt
import seaborn as sns
import pandas as pd
import os
from datetime import datetime
from sklearn.metrics import classification_report, confusion_matrix
from preprocessed import train_data, val_data, test_data, class_names

class F1Score(metrics.Metric):
    def __init__(self, name='f1_score', **kwargs):
        super().__init__(name=name, **kwargs)
        self.precision = metrics.Precision()
        self.recall = metrics.Recall()

    def update_state(self, y_true, y_pred, sample_weight=None):
        self.precision.update_state(y_true, y_pred, sample_weight)
        self.recall.update_state(y_true, y_pred, sample_weight)

    def result(self):
        p = self.precision.result()
        r = self.recall.result()
        return 2 * ((p * r) / (p + r + 1e-6))

tf.keras.mixed_precision.set_global_policy('mixed_float16')
print("Mixed precision enabled:", tf.keras.mixed_precision.global_policy())

# directory
FINE_TUNE_DIR = "efficientnet_finetuned_results"
os.makedirs(FINE_TUNE_DIR, exist_ok=True)

# original trained model
original_model = load_model("efficientnet_results/efficientnet_model.keras")

base_model = EfficientNetB0(include_top=False, input_shape=(224, 224, 3), weights=None)

# Transfer weights safely by name
for layer in base_model.layers:
    try:
        orig_layer = original_model.get_layer(name=layer.name)
        layer.set_weights(orig_layer.get_weights())
    except ValueError:
        print(f"[SKIP] Layer {layer.name} not found in original_model")
    except Exception as e:
        print(f"[ERROR] Could not set weights for {layer.name}: {e}")

# Unfreezing top 50 layers for fine-tuning
base_model.trainable = True
for layer in base_model.layers[:-50]:
    layer.trainable = False

# Rebuilds classification head
x = tf.keras.layers.GlobalAveragePooling2D()(base_model.output)
x = tf.keras.layers.Dense(128, activation='swish', kernel_regularizer=tf.keras.regularizers.l2(1e-4))(x)
x = tf.keras.layers.Dropout(0.3)(x)
x = tf.keras.layers.BatchNormalization()(x)
predictions = tf.keras.layers.Dense(
    len(class_names), 
    activation='softmax',
    dtype='float32'  # Explicitly set dtype
)(x)

# Final model
model = tf.keras.Model(inputs=base_model.input, outputs=predictions)

optimizer = tf.keras.optimizers.AdamW(
    learning_rate=tf.keras.optimizers.schedules.CosineDecay(
        initial_learning_rate=1e-4,
        decay_steps=len(train_data) * 20,),
    weight_decay=1e-5,
    clipnorm=1.0
)

model.compile(
    optimizer = optimizer,
    loss='categorical_crossentropy',
    metrics=['accuracy', F1Score()]
)

# Callbacks 
callbacks = [
    tf.keras.callbacks.EarlyStopping(
        patience=5,
        restore_best_weights=True,
        monitor='val_accuracy',
        mode = 'max',
        min_delta=0.005
    ),
    tf.keras.callbacks.ModelCheckpoint(
        f"{FINE_TUNE_DIR}/best_finetuned_model.keras",
        save_best_only=True,
        mode = 'max',
        monitor='val_f1_score'
    ),
    tf.keras.callbacks.ReduceLROnPlateau(
        monitor='val_loss',
        factor=0.1,
        patience=3,
        min_lr=1e-6
    ),
    tf.keras.callbacks.CSVLogger(f"{FINE_TUNE_DIR}/finetuning_log.csv")
]

# Finetune training
history = model.fit(
    train_data,
    validation_data = val_data,
    epochs = 30,  # increased epochs 
    callbacks = callbacks,
    verbose = 1
)

# Evaluation 
def evaluate_model(model, dataset, class_names, results_dir):
    y_true, y_pred = [], []
    for images, labels in dataset:
        y_true.extend(tf.argmax(labels, axis=1).numpy())
        y_pred.extend(tf.argmax(model.predict(images, verbose=0), axis=1))
    
    report = classification_report(y_true, y_pred, target_names=class_names, output_dict=True)
    pd.DataFrame(report).transpose().to_csv(f"{results_dir}/finetuned_classification_report.csv")
    
    plt.figure(figsize=(12, 10))
    cm = confusion_matrix(y_true, y_pred)
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues',
                xticklabels=class_names, yticklabels=class_names)
    plt.xticks(rotation=45, ha='right')
    plt.tight_layout()
    plt.savefig(f"{results_dir}/finetuned_confusion_matrix.png")
    plt.close()
    
    return report

# Evaluate & save
report = evaluate_model(model, test_data, class_names, FINE_TUNE_DIR)

# History plot
def plot_history(history, results_dir):
    plt.figure(figsize=(15, 5))
    
    # Accuracy
    plt.subplot(1, 2, 1)
    plt.plot(history.history['accuracy'], label='Train')
    plt.plot(history.history['val_accuracy'], label='Validation')
    plt.title('Accuracy Curves')
    plt.ylabel('Accuracy')
    plt.xlabel('Epoch')
    plt.legend()
    
    # Loss
    plt.subplot(1, 2, 2)
    plt.plot(history.history['loss'], label='Train')
    plt.plot(history.history['val_loss'], label='Validation')
    plt.title('Loss Curves')
    plt.ylabel('Loss')
    plt.xlabel('Epoch')
    plt.legend()
    
    plt.tight_layout()
    plt.savefig(f"{results_dir}/finetuned_training_history.png")
    plt.close()

plot_history(history, FINE_TUNE_DIR)

# comparison metrics
def save_comparison_metrics(original_metrics, finetuned_metrics):
    comparison = {
        'metric': ['test_accuracy', 'average_precision', 'average_recall', 'average_f1'],
        'original': [
            original_metrics['test_accuracy'],
            original_metrics['average_precision'],
            original_metrics['average_recall'],
            original_metrics['average_f1']
        ],
        'finetuned': [
            finetuned_metrics['test_accuracy'],
            finetuned_metrics['average_precision'],
            finetuned_metrics['average_recall'],
            finetuned_metrics['average_f1']
        ],
        'improvement': [
            finetuned_metrics['test_accuracy'] - original_metrics['test_accuracy'],
            finetuned_metrics['average_precision'] - original_metrics['average_precision'],
            finetuned_metrics['average_recall'] - original_metrics['average_recall'],
            finetuned_metrics['average_f1'] - original_metrics['average_f1']
        ]
    }
    pd.DataFrame(comparison).to_csv(f"{FINE_TUNE_DIR}/performance_comparison.csv", index=False)

# original metrics
original_metrics = pd.read_csv("efficientnet_results/efficientnet_metrics.csv").iloc[0].to_dict()

# finetuned metrics
finetuned_metrics = {
    'training_accuracy': history.history['accuracy'][-1],
    'validation_accuracy': history.history['val_accuracy'][-1],
    'test_accuracy': report['accuracy'],
    'average_precision': report['macro avg']['precision'],
    'average_recall': report['macro avg']['recall'],
    'average_f1': report['macro avg']['f1-score'],
    'timestamp': datetime.now().strftime("%Y-%m-%d %H:%M:%S")
}
pd.DataFrame([finetuned_metrics]).to_csv(f"{FINE_TUNE_DIR}/finetuned_metrics.csv", index=False)

save_comparison_metrics(original_metrics, finetuned_metrics)

# Model Saved
model.save(f"{FINE_TUNE_DIR}/EfficientNet_finetuned.keras")


converter = tf.lite.TFLiteConverter.from_keras_model(model)


converter.optimizations = [tf.lite.Optimize.DEFAULT]  # Activates float16 quantization
converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS]  # Ensures CPU fallback
converter.experimental_new_converter = True  # Better conversion

tflite_model = converter.convert()

interpreter = tf.lite.Interpreter(model_content=tflite_model)
interpreter.allocate_tensors()
input_details = interpreter.get_input_details()
print("Input dtype:", input_details[0]['dtype'])  # Should match training (float32)

# model saved
with open(f"{FINE_TUNE_DIR}/EfficientNet_quantized.tflite", "wb") as f:
    f.write(tflite_model)

print("\nFine-tuning results saved in 'efficientnet_finetuned_results' directory:")
print(f"- finetuned_classification_report.csv")
print(f"- finetuned_confusion_matrix.png")
print(f"- finetuned_training_history.png")
print(f"- finetuned_metrics.csv")
print(f"- performance_comparison.csv")
print(f"- best_finetuned_model.keras (best checkpoint)")
print(f"- EfficientNet_finetuned.keras (final weights)")