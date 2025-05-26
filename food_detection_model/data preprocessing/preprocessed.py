import os
import numpy as np
import tensorflow as tf
from collections import Counter
import matplotlib.pyplot as plt
import seaborn as sns
import csv
from datetime import datetime

# Dataset path
train_dir = "D:\\Rabeesha\\University\\Semester_6\\AI_CV\\archive\\train"
val_dir = "D:\\Rabeesha\\University\\Semester_6\\AI_CV\\archive\\validation"
test_dir = "D:\\Rabeesha\\University\\Semester_6\\AI_CV\\archive\\test"

image_size = (224, 224) 
batch_size = 16
seed = 42

#  datasets
train_data = tf.keras.preprocessing.image_dataset_from_directory(
    train_dir,
    image_size=image_size,
    batch_size=batch_size,
    label_mode="categorical",
    shuffle=True,  # Enable shuffling
    seed=42,       # Ensures reproducibility
)

class_names = train_data.class_names

val_data = tf.keras.preprocessing.image_dataset_from_directory(
    val_dir,
    image_size=image_size,
    batch_size=batch_size,
    label_mode="categorical",
    shuffle=False  
)

test_data = tf.keras.preprocessing.image_dataset_from_directory(
    test_dir,
    image_size=image_size,
    batch_size=batch_size,
    label_mode="categorical",
    shuffle=False
)

# Data augmentation 
augmentation = tf.keras.Sequential([
    tf.keras.layers.RandomFlip("horizontal"),
    tf.keras.layers.RandomRotation(0.05),              
    tf.keras.layers.RandomZoom(0.05),                     

    tf.keras.layers.RandomBrightness(0.08),              
    tf.keras.layers.RandomContrast(0.08),            
    tf.keras.layers.RandomSaturation(0.05),              
    
    tf.keras.layers.GaussianNoise(0.005),
     tf.keras.layers.Rescaling(1./127.5, offset=-1),  # Normalize
])               

def augment_data(images, labels):
    augmented_images = tf.vectorized_map(
        lambda x: augmentation(x), 
        images
    )
    return augmented_images, labels

# Visualize augmented images
for images, _ in train_data.take(1):
    augmented = augmentation(images[0])
    plt.imshow(augmented.numpy().astype("uint8"))
    plt.show()

# Applying augmentation only to training data
train_data = (
    train_data
    .map(augment_data, num_parallel_calls=tf.data.AUTOTUNE)
    .cache()
    .shuffle(1000, reshuffle_each_iteration=True)
    .prefetch(tf.data.AUTOTUNE)
)

# prefetching data to improve performance
val_data = val_data.prefetch(tf.data.AUTOTUNE)
test_data = test_data.prefetch(tf.data.AUTOTUNE)


# Class distribution
def class_distribution(dataset):
    counts = Counter()
    for _, labels in dataset:
        class_indices = np.argmax(labels.numpy(), axis=1)
        counts.update(class_indices)
    return counts

train_counts = class_distribution(train_data)
test_counts = class_distribution(test_data)
val_counts = class_distribution(val_data)

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
    with open(filename, mode='w', newline='') as csvfile:
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

print("Train:", len(os.listdir(os.path.join(train_dir, "Aloo gobi"))))
print("Val:", len(os.listdir(os.path.join(val_dir, "Aloo gobi"))))
print("Test:", len(os.listdir(os.path.join(test_dir, "Aloo gobi"))))

print("Class names:", class_names)  

print("Total train images:", sum(train_counts.values()))  # 1,360 (17×80)
print("Total val images:", sum(val_counts.values()))      # 170 (17×10)
print("Total test images:", sum(test_counts.values()))    # 170 (17×10)

# Exporting the processed data and class names
__all__ = ['train_data', 'val_data', 'test_data', 'class_names']