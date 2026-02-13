# GitHub Pages Setup Instructions

This repository now includes a GitHub Pages site showcasing the CQEngine Next project.

## 🚀 Deploying GitHub Pages

To deploy the GitHub Pages site, follow these steps:

### Option 1: Using the gh-pages branch (Recommended)

The `gh-pages` branch has been created locally with the `index.html` file. To deploy:

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

### Option 2: Using GitHub Actions (Alternative)

Alternatively, you can use GitHub Actions to deploy from the main branch:

1. Go to `Settings` > `Pages`
2. Under "Source", select "GitHub Actions"
3. The `index.html` file can be deployed from any branch using a workflow

### Verification

After deployment, your site should be available at:
```
https://msaifasif.github.io/cqengine-next/
```

It may take a few minutes for the site to become available after the first deployment.

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

1. Make changes to `index.html` on the `gh-pages` branch
2. Commit and push the changes:
   ```bash
   git checkout gh-pages
   git add index.html
   git commit -m "Update GitHub Pages site"
   git push origin gh-pages
   ```

3. The site will automatically update within a few minutes

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
