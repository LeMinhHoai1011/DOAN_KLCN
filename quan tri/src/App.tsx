import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Documents from './pages/Documents';
import DocumentDetail from './pages/DocumentDetail';
import Upload from './pages/Upload';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Layout />}>
          <Route index element={<Dashboard />} />
          <Route path="documents" element={<Documents />} />
          <Route path="documents/:id" element={<DocumentDetail />} />
          <Route path="upload" element={<Upload />} />
          <Route path="ocr-ai" element={<div className="p-6">OCR & AI Tracking placeholder</div>} />
          <Route path="storage" element={<div className="p-6">Storage placeholder</div>} />
          <Route path="classification" element={<div className="p-6">Classification placeholder</div>} />
          <Route path="reports" element={<div className="p-6">Reports placeholder</div>} />
          <Route path="settings" element={<div className="p-6">Settings placeholder</div>} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}

export default App;
