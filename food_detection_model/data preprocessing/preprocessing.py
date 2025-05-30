import os
import numpy as np
import tensorflow as tf
from collections import Counter
import matplotlib.pyplot as plt
import seaborn as sns
from PIL import Image
import csv
from datetime import datetime

# Dataset path
data = "D:\\Rabeesha\\University\\Semester_6\\AI_CV\\archive\\dataset\\dataset"
image_size = (224, 224) 
batch_size = 32
seed = 42

# (80% training, 20% validation)
train_data = tf.keras.preprocessing.image_dataset_from_directory(
    data,
    validation_split=0.2,
    subset="training",
    seed=seed,
    image_size=image_size,
    batch_size=batch_size,
    label_mode="categorical",
)
class_names = train_data.class_names

temp_data = tf.keras.preprocessing.image_dataset_from_directory(
    data,
    validation_split=0.2,  # Same 20% as above
    subset="validation",
    seed=seed,
    image_size=image_size,
    batch_size=batch_size,
    label_mode="categorical",
)

# Split data (20%) into validation (10%) and test (10%)
val_test_batches = len(temp_data)
validation_data = temp_data.take(val_test_batches // 2)
test_data = temp_data.skip(val_test_batches // 2)

# Data augmentation 
augmentation = tf.keras.Sequential([
    tf.keras.layers.RandomFlip("horizontal_and_vertical"),
    tf.keras.layers.RandomRotation(0.15),
    tf.keras.layers.RandomZoom(0.1),
    tf.keras.layers.RandomBrightness(0.15),
    tf.keras.layers.RandomContrast(0.1),
])

def augment_data(image, label):
    return augmentation(image), label

train_data = train_data.map(augment_data).prefetch(tf.data.AUTOTUNE)
validation_data = validation_data.prefetch(tf.data.AUTOTUNE)
test_data = test_data.prefetch(tf.data.AUTOTUNE)

# Applying augmentation only to training data
train_data = train_data.map(augment_data)

# Class distribution
def class_distribution(dataset):
    counts = Counter()
    for _, labels in dataset:
        class_indices = np.argmax(labels.numpy(), axis=1)
        counts.update(class_indices)
    return counts

train_counts = class_distribution(train_data)
test_counts = class_distribution(test_data)
val_counts = class_distribution(validation_data)

# Plot distribution
def plot_graph(counts, title, classes):
    plt.figure(figsize=(12, 6))
    sns.barplot(x=[classes[i] for i in counts.keys()], 
                y=list(counts.values()))
    plt.title(title)
    plt.xlabel("Classes")
    plt.ylabel("No. of Images")
    plt.xticks(rotation=90)
    plt.tight_layout()
    plt.show()

plot_graph(train_counts, "Training Data Distribution", class_names)
plot_graph(val_counts, "Validation Data Distribution", class_names)
plot_graph(test_counts, "Test Data Distribution", class_names)

# dataset statistics - csv 
def save_to_csv(data_dict, filename="dataset_stats.csv"):
    file_exists = os.path.isfile(filename)
    with open(filename, mode='a', newline='') as csvfile:
        writer = csv.writer(csvfile)
        if not file_exists:
            writer.writerow(["Timestamp"] + list(data_dict.keys()))
        writer.writerow([datetime.now().strftime("%Y-%m-%d %H:%M:%S")] + list(data_dict.values()))

stats = {
    "Num Classes": len(class_names),
    "Training Samples": sum(train_counts.values()),
    "Validation Samples": sum(val_counts.values()),
    "Test Samples": sum(test_counts.values()),
    "Batch Size": batch_size
}

save_to_csv(stats)
print("\nDataset statistics saved !!")

# Exporting the processed data and class names
__all__ = ['train_data', 'validation_data', 'test_data', 'class_names']