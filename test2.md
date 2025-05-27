# NutriVision: Nutritional Assistant Project Documentation

## Project Overview
NutriVision is an advanced nutritional analysis system that combines multiple AI models and agents to provide comprehensive recipe analysis and nutritional guidance.

## System Architecture

### High-Level System Flow
```mermaid
flowchart TD
    A[Data Collection & Preprocessing] --> B[Vector Database Creation]
    B --> C[Model Development & Testing]
    C --> D[Agent System]
    D --> E[Evaluation System]

    subgraph "Data Pipeline"
        A1[Recipe Dataset] --> A2[Data Cleaning]
        A2 --> A3[Visualization]
        A3 --> A4[Vector Embeddings]
    end

    subgraph "Model Pipeline"
        C1[Model Testing] --> C2[Model Selection]
        C2 --> C3[Fine-tuning]
        C3 --> C4[Deployment]
    end

    subgraph "Agent System"
        D1[Recipe Agent] --> D2[GI Analysis Agent]
        D2 --> D3[Grader Agent]
    end
```

### Detailed Model Development Pipeline
```mermaid
flowchart TD
    subgraph "Model Development & Deployment"
        A[Initial Models] --> B[Performance Evaluation]
        B --> C[Model Selection]
        C --> D[Fine-tuning Pipeline]
        
        subgraph "Fine-tuning Process"
            D --> E[Data Preparation]
            E --> F[Training Configuration]
            F --> G[Weights & Biases Integration]
            G --> H[Training Monitoring]
            H --> I[Model Checkpoints]
        end
        
        I --> J[Hugging Face Deployment]
        J --> K[Model Registry]
        K --> L[Inference Endpoints]
    end

    subgraph "Monitoring & Visualization"
        G --> M[W&B Dashboard]
        M --> N[Training Metrics]
        M --> O[Validation Metrics]
        M --> P[Resource Usage]
    end
```

### Agent System Architecture
```mermaid
flowchart TD
    subgraph "Agent System"
        A[Recipe Agent] --> B[Vector DB Query]
        B --> C[Recipe Analysis]
        
        C --> D[GI Analysis Agent]
        D --> E[GI Calculation]
        E --> F[Nutritional Impact]
        
        F --> G[Grader Agent]
        G --> H[Ensemble Grading]
        H --> I[Quality Validation]
    end

    subgraph "External Services"
        J[Hugging Face Model] --> D
        K[Chroma DB] --> B
        L[Ollama Local] --> F
    end
```

## Detailed Workflow

### 1. Data Preprocessing Pipeline
- **Recipe Dataset Processing**
  - Collection and cleaning of recipe data
  - Standardization of formats
  - Data validation and quality checks
  - Creation of visualizations for data analysis

- **Vector Database Creation**
  - Generation of vector embeddings using LLM
  - Creation of Chroma database for efficient storage
  - Implementation of similarity search capabilities

### 2. Model Development & Testing
- **Initial Model Testing**
  - Deployment of multiple models on inference endpoints:
    - DeepSeek
    - ChatGPT
    - RoBERTa
  - Performance comparison and evaluation
  - Selection of RoBERTa as primary model

- **Model Fine-tuning**
  - GPU-based fine-tuning on Google Colab
  - Integration with Weights & Biases for:
    - Training metrics visualization
    - Resource utilization tracking
    - Model performance monitoring
    - Hyperparameter optimization
  - Deployment to Hugging Face:
    - Model versioning
    - Inference endpoint creation
    - API integration
  - Performance validation

### 3. Agent System Implementation
- **Recipe Agent**
  - Queries vector embeddings
  - Recipe retrieval and analysis
  - Nutritional information extraction

- **GI Analysis Agent**
  - Glycemic Index calculation
  - Recipe grading based on GI values
  - Nutritional impact assessment

- **Grader Agent System**
  - Implementation of ensemble grading
  - Integration of two Claude models
  - Quality assurance and validation

### 4. Testing & Evaluation
- **Comprehensive Testing**
  - Test categories:
    - Simple recipes
    - High GI recipes
    - Low GI recipes
  - Performance metrics collection
  - Error analysis and improvement

- **Evaluation Framework**
  - Ensemble grader agent implementation
  - Cross-validation with expert assessments
  - Performance benchmarking

## Technical Components

### Data Processing
- Vector embeddings generation
- Chroma database implementation
- Local Ollama integration for nutritional guidelines

### Model Architecture
- RoBERTa fine-tuning pipeline
  - Weights & Biases integration for monitoring
  - Training visualization and metrics tracking
  - Model checkpoint management
- Hugging Face deployment
  - Model versioning and registry
  - Inference endpoint configuration
  - API integration
- Model evaluation framework

### Agent System
- Recipe querying system
- GI analysis pipeline
- Grading mechanism

## Performance Analysis
- Model accuracy metrics
- Processing time optimization
- Resource utilization
- Weights & Biases dashboard metrics:
  - Training loss curves
  - Validation metrics
  - Resource consumption
  - Model performance comparisons

## Future Enhancements
- Additional model integrations
- Extended recipe database
- Enhanced nutritional analysis capabilities
- Advanced monitoring and visualization features 