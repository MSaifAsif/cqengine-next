# GitHub Pages Setup Instructions

This repository now includes a GitHub Pages site showcasing the CQEngine Next project.

## 🚀 Deploying GitHub Pages

To deploy the GitHub Pages site, follow these steps:

### Using the gh-pages branch (Recommended)

The `gh-pages` branch has been created as an **orphan branch** containing only the website files (no source code or documentation). To deploy:

1. Push the `gh-pages` branch to GitHub:
   ```bash
   git push origin gh-pages
   ```

2. Go to your repository settings on GitHub:
   - Navigate to `Settings` > `Pages`
   - Under "Source", select the `gh-pages` branch
   - Select the root folder `/`
   - Click "Save"

3. GitHub will automatically deploy your site to:
   ```
   https://msaifasif.github.io/cqengine-next/
   ```

### Clean gh-pages Branch Structure

The `gh-pages` branch contains **only** the files needed for the website:
```
gh-pages/
├── index.html                                           # Main website file
└── documentation/
    └── images/
        └── quantized-navigable-index-carid-between.png  # Performance chart
```

This clean structure ensures fast loading and keeps the website separate from the source code.


### Verification

After deployment, your site should be available at:
```
https://msaifasif.github.io/cqengine-next/
```

It may take a few minutes for the site to become available after the first deployment.

## 🧹 Why a Clean gh-pages Branch?

The `gh-pages` branch is an **orphan branch** with no shared history with the main development branches. This provides several benefits:

- **Faster loading**: Only website files are present (2 files vs 100+ source files)
- **Cleaner deployments**: No build artifacts, source code, or test files
- **Better separation**: Website content is completely separate from application code
- **Smaller repository**: When cloning just the gh-pages branch, users get only what they need

## 📄 Site Contents

The GitHub Pages site includes:

- **Hero Section**: Project overview and quick links
- **Key Features**: Highlighted features with icons and descriptions
- **Performance Benchmarks**: Statistics and performance charts from the documentation
- **Use Cases**: Real-world applications and use cases
- **Quick Start**: Maven dependency and code examples
- **Real-World Usage**: Testimonials from companies using CQEngine
- **Why This Fork**: Information about the maintained fork
- **Footer**: Comprehensive links to documentation and resources

## 🔄 Updating the Site

To update the GitHub Pages site:

1. Switch to the gh-pages branch:
   ```bash
   git checkout gh-pages
   ```

2. Make changes to `index.html` or add new images to `documentation/images/`

3. Commit and push the changes:
   ```bash
   git add .
   git commit -m "Update GitHub Pages site"
   git push origin gh-pages
   ```

4. The site will automatically update within a few minutes

**Note**: The gh-pages branch is separate from the main development branches. To update content from README or documentation, you'll need to manually copy and update the relevant sections in `index.html`.

## 📸 Preview

The site features a modern, responsive design with:
- Purple gradient theme matching the project branding
- Mobile-responsive layout
- Performance statistics and benchmarks
- Code examples with syntax highlighting
- Links to all documentation and resources

## 🎨 Customization

The site uses inline CSS for easy maintenance. To customize:

- Colors and gradients are in the `<style>` section
- Update content directly in the HTML
- Images are referenced from the `documentation/images/` folder
- Badges are pulled from shields.io

## 📝 Notes

- The site is a single-page HTML file (`index.html`)
- All CSS is embedded for simplicity
- Images are referenced relative to the repository structure
- External badges use shields.io for real-time data
