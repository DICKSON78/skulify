import { Fragment } from 'react';
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import { AuthProvider } from '../auth/AuthContext';
import ProtectedRoute from '../auth/ProtectedRoute';
import Login from '../pages/Login';
import Dashboard from '../pages/Dashboard';
import ClassAttendance from '../pages/ClassAttendance';
import PocketMoney from '../pages/PocketMoney';
import CashRequests from '../pages/CashRequests';
import LibraryDesk from '../pages/LibraryDesk';
import RetailStorePos from '../pages/RetailStorePos';
import RetailStoreItems from '../pages/RetailStoreItems';
import RetailTerminals from '../pages/RetailTerminals';
import RetailInventory from '../pages/RetailInventory';
import RetailStockMovements from '../pages/RetailStockMovements';
import RetailPurchaseRequests from '../pages/RetailPurchaseRequests';
import OperationsHub from '../pages/OperationsHub';
import TimetableGenerator from '../pages/TimetableGenerator';
import MarksUpload from '../pages/MarksUpload';
import DutyRoster from '../pages/DutyRoster';
import ResultsPlanning from '../pages/ResultsPlanning';
import ParentPortal from '../pages/ParentPortal';
import SchoolDocuments from '../pages/SchoolDocuments';
import OnlineTests from '../pages/OnlineTests';
import TakeTest from '../pages/TakeTest';
import ResourcePage from '../components/ResourcePage';
import ResourceFormPage from '../components/ResourceFormPage';
import ResourceViewPage from '../components/ResourceViewPage';
import { RESOURCE_CONFIGS } from '../config/resources';

/** These six paths have purpose-built pages; skip their generated CRUD routes. */
const CUSTOM_RESOURCE_ROUTES = new Set([
    'store-pos',
    'store-items',
    'store-terminals',
    'inventory-items',
    'stock-movements',
    'purchase-requests',
]);

const generatedConfigs = Object.values(RESOURCE_CONFIGS).filter(
    (config) => !CUSTOM_RESOURCE_ROUTES.has(config.resource),
);

export default function AppRouter() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/login" element={<Login />} />
          <Route path="/test/:token" element={<TakeTest />} />
          <Route
            path="/dashboard"
            element={<ProtectedRoute path="/dashboard"><Dashboard /></ProtectedRoute>}
          />
          <Route
            path="/pocket-money"
            element={<ProtectedRoute path="/pocket-money"><PocketMoney /></ProtectedRoute>}
          />
          <Route
            path="/cash-requests"
            element={<ProtectedRoute path="/cash-requests"><CashRequests /></ProtectedRoute>}
          />
          <Route
            path="/library-desk"
            element={<ProtectedRoute path="/library-desk"><LibraryDesk /></ProtectedRoute>}
          />
          <Route
            path="/store-pos"
            element={<ProtectedRoute path="/store-pos"><RetailStorePos /></ProtectedRoute>}
          />
          <Route
            path="/store-items"
            element={<ProtectedRoute path="/store-items"><RetailStoreItems /></ProtectedRoute>}
          />
          <Route
            path="/store-terminals"
            element={<ProtectedRoute path="/store-terminals"><RetailTerminals /></ProtectedRoute>}
          />
          <Route
            path="/inventory-items"
            element={<ProtectedRoute path="/inventory-items"><RetailInventory /></ProtectedRoute>}
          />
          <Route
            path="/stock-movements"
            element={<ProtectedRoute path="/stock-movements"><RetailStockMovements /></ProtectedRoute>}
          />
          <Route
            path="/purchase-requests"
            element={<ProtectedRoute path="/purchase-requests"><RetailPurchaseRequests /></ProtectedRoute>}
          />
          <Route
            path="/operations-hub"
            element={<ProtectedRoute path="/operations-hub"><OperationsHub /></ProtectedRoute>}
          />
          <Route
            path="/timetable-generator"
            element={<ProtectedRoute path="/timetable-generator"><TimetableGenerator /></ProtectedRoute>}
          />
          <Route
            path="/marks-upload"
            element={<ProtectedRoute path="/marks-upload"><MarksUpload /></ProtectedRoute>}
          />
          <Route
            path="/duty-roster"
            element={<ProtectedRoute path="/duty-roster"><DutyRoster /></ProtectedRoute>}
          />
          <Route
            path="/results-planning"
            element={<ProtectedRoute path="/results-planning"><ResultsPlanning /></ProtectedRoute>}
          />
          <Route
            path="/parent-portal"
            element={<ProtectedRoute path="/parent-portal"><ParentPortal /></ProtectedRoute>}
          />
          <Route
            path="/online-tests"
            element={<ProtectedRoute path="/online-tests"><OnlineTests /></ProtectedRoute>}
          />
          <Route
            path="/school-documents"
            element={<ProtectedRoute path="/school-documents"><SchoolDocuments /></ProtectedRoute>}
          />

          <Route
            path="/attendance"
            element={<ProtectedRoute path="/attendance"><ClassAttendance /></ProtectedRoute>}
          />

          {generatedConfigs.map((config) => (
            <Fragment key={config.resource}>
              <Route
                path={`/${config.resource}`}
                element={
                  <ProtectedRoute path={`/${config.resource}`}>
                    <ResourcePage config={config} />
                  </ProtectedRoute>
                }
              />
              <Route
                path={`/${config.resource}/new`}
                element={
                  <ProtectedRoute path={`/${config.resource}`}>
                    <ResourceFormPage config={config} mode="create" />
                  </ProtectedRoute>
                }
              />
              <Route
                path={`/${config.resource}/:id`}
                element={
                  <ProtectedRoute path={`/${config.resource}`}>
                    <ResourceViewPage config={config} />
                  </ProtectedRoute>
                }
              />
              <Route
                path={`/${config.resource}/:id/edit`}
                element={
                  <ProtectedRoute path={`/${config.resource}`}>
                    <ResourceFormPage config={config} mode="edit" />
                  </ProtectedRoute>
                }
              />
            </Fragment>
          ))}

          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
